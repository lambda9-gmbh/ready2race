package de.lambda9.ready2race.backend.app.eventInfo.boundary

import com.fasterxml.jackson.databind.ObjectMapper
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.club.boundary.ClubComposition
import de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameSettings
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.InfoViewConfigurationRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.toAthleteBoardMatch
import de.lambda9.ready2race.backend.app.eventInfo.control.toAthleteBoardResult
import de.lambda9.ready2race.backend.app.eventInfo.control.toDto
import de.lambda9.ready2race.backend.app.eventInfo.control.toLiveMatchInfo
import de.lambda9.ready2race.backend.app.eventInfo.control.toRecord
import de.lambda9.ready2race.backend.app.eventInfo.entity.*
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.EventScheduleLogic
import de.lambda9.ready2race.backend.app.eventSchedule.control.EventScheduleRepo
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.data.Timecode
import de.lambda9.ready2race.backend.database.generated.enums.InfoViewType
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import org.jooq.JSONB
import org.jooq.Record
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object EventInfoService {

    // Eine Instanz genügt: ObjectMapper ist threadsicher und wird von einem öffentlichen
    // Endpoint bei einer Regatta im Sekundentakt aufgerufen.
    private val objectMapper = ObjectMapper()

    // Zwischenspeicher der Athleten-Anzeige je Veranstaltung. Ohne ihn löst jeder Abruf
    // rund ein Dutzend Datenbankabfragen aus — bei 200 Telefonen im 15-Sekunden-Takt
    // landet das ungebremst auf der Datenbank. Mit ihm zahlt die Datenbank höchstens
    // einmal je CACHE_TTL_SECONDS und Veranstaltung, egal wie viele Zuschauer laden.
    // Nur echte, per EventRepo geprüfte Veranstaltungen landen hier, die Karte bleibt
    // also klein; abgelaufene Einträge werden beim nächsten Abruf überschrieben.
    private data class CachedBoard(val builtAt: LocalDateTime, val dto: AthleteBoardDto)

    private val athleteBoardCache = ConcurrentHashMap<UUID, CachedBoard>()

    // Zwischenspeicher der Live-Liste je Veranstaltung UND [limit]. Derselbe Grund wie beim
    // Athleten-Board: der Endpoint ist öffentlich, verlangt keine Anmeldung und wird vom
    // Frontend im 15-Sekunden-Takt abgerufen (usePolledFetch in ResultsLiveMatches.tsx) - bei
    // 200 Telefonen am Ufer sonst mehrere hundert Abrufe je Sekunde, jeder davon mit
    // clubShortNames(), zwei Lauf-Abfragen, je EINER Mannschaftsabfrage PRO LAUF sowie
    // getShowBreaksOnPublicBoards und EventScheduleRepo.getSlots. Der Schlüssel trägt [limit]
    // mit, weil verschiedene Aufrufer (künftig oder heute schon mit abweichender Seitengröße)
    // sonst den falsch bemessenen Stand eines anderen bekämen.
    private data class CachedLiveMatches(val builtAt: LocalDateTime, val dto: ApiResponse.ListDto<LiveMatchInfo>)

    private val liveMatchesCache = ConcurrentHashMap<Pair<UUID, Int>, CachedLiveMatches>()

    // Info View Configuration Methods

    fun getInfoViews(
        eventId: UUID,
        includeInactive: Boolean = false
    ): App<EventInfoProblem, ApiResponse.ListDto<InfoViewConfigurationDto>> =
        KIO.comprehension {
            val exists = !EventRepo.exists(eventId).orDie()
            if (!exists) {
                !KIO.fail<EventInfoProblem>(EventInfoProblem.EventNotFound(eventId))
            }

            val views = !InfoViewConfigurationRepo.findByEvent(eventId, includeInactive).orDie()
            KIO.ok(ApiResponse.ListDto(views.map { it.toDto() }))
        }

    fun createInfoView(
        eventId: UUID,
        request: InfoViewConfigurationRequest
    ): App<EventInfoProblem, ApiResponse.Created> = KIO.comprehension {
        // Validate event exists
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            !KIO.fail<EventInfoProblem>(EventInfoProblem.EventNotFound(eventId))
        }

        // Validate request
        if (request.displayDurationSeconds <= 0) {
            !KIO.fail<EventInfoProblem>(EventInfoProblem.InvalidViewConfiguration("Display duration must be positive"))
        }
        if (request.dataLimit <= 0 || request.dataLimit > 100) {
            !KIO.fail<EventInfoProblem>(EventInfoProblem.InvalidViewConfiguration("Data limit must be between 1 and 100"))
        }

        val record = request.toRecord(eventId)
        val created = !InfoViewConfigurationRepo.create(record).orDie()
        KIO.ok(ApiResponse.Created(created))
    }

    fun updateInfoView(
        id: UUID,
        request: InfoViewConfigurationRequest
    ): App<EventInfoProblem, ApiResponse.NoData> = KIO.comprehension {
        val existing = !InfoViewConfigurationRepo.findById(id).orDie()
        if (existing == null) {
            !KIO.fail<EventInfoProblem>(EventInfoProblem.InfoViewConfigurationNotFound(id))
        }

        val updated = !InfoViewConfigurationRepo.update(id) {
            viewType = request.viewType
            displayDurationSeconds = request.displayDurationSeconds
            dataLimit = request.dataLimit
            filters = request.filters?.let { JSONB.jsonb(it.toString()) }
            sortOrder = request.sortOrder
            isActive = request.isActive
            updatedAt = LocalDateTime.now()
        }.orDie()

        if (updated == null) {
            KIO.fail<EventInfoProblem>(EventInfoProblem.InfoViewConfigurationNotFound(id))
        } else {
            KIO.ok(ApiResponse.NoData)
        }
    }

    fun deleteInfoView(id: UUID): App<EventInfoProblem, ApiResponse.NoData> = KIO.comprehension {
        val existing = !InfoViewConfigurationRepo.exists(id).orDie()

        if (!existing) {
            !KIO.fail<EventInfoProblem>(EventInfoProblem.InfoViewConfigurationNotFound(id))
        }

        !InfoViewConfigurationRepo.delete(id).orDie()

        noData
    }

    // Data Fetching Methods


    /**
     * Die Ergebnisse, die öffentlich gezeigt werden dürfen. Ab welchem Zustand ein Lauf dazugehört,
     * entscheidet die Veranstaltung über `Event.publicResultsVisibility` — die Begründung für die
     * Regel und die Voreinstellung steht bei [AthleteBoardLogic.isPublicResult] und in Migration
     * V202608061200. Bewusst hier und nicht in der Ansichts-Konfiguration: dieser Endpoint bedient
     * auch die öffentliche Ergebnisseite, die gar keine `info_view_configuration`-Zeile hat.
     *
     * Das Schiedsrichter-Dashboard geht einen anderen Weg (LiveDashboardService) und bleibt
     * unberührt — dort ist ohnehin alles sichtbar.
     */
    fun getLatestMatchResults(
        eventId: UUID,
        limit: Int = 10,
        competitionId: UUID?,
    ): App<Nothing, ApiResponse.ListDto<LatestMatchResultInfo>> = KIO.comprehension {
        getLatestMatchResults(eventId, limit, competitionId, !clubShortNames())
    }

    private fun getLatestMatchResults(
        eventId: UUID,
        limit: Int,
        competitionId: UUID?,
        clubShortNames: ClubShortNameSettings,
    ): App<Nothing, ApiResponse.ListDto<LatestMatchResultInfo>> = KIO.comprehension {

        val visibility = !EventRepo.getPublicResultsVisibility(eventId).orDie()
        val matches = !CompetitionMatchRepo.getMatchResults(eventId, competitionId, limit, visibility).orDie()

        val result = matches.map { match ->
            val matchId = match[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!
            val teams = !getMatchResultTeams(matchId, clubShortNames)

            LatestMatchResultInfo(
                matchId = matchId,
                competitionId = match.get("competition_id", UUID::class.java)!!,
                competitionName = match.get("competition_name", String::class.java) ?: "",
                categoryName = match[COMPETITION_VIEW.CATEGORY_NAME],
                roundName = match.get("round_name", String::class.java),
                matchName = match.get("match_name", String::class.java),
                matchNumber = null, // Could be parsed from match name if needed
                updatedAt = match[COMPETITION_MATCH.UPDATED_AT]!!,
                startTime = match[COMPETITION_MATCH.START_TIME],
                startedAt = match[COMPETITION_MATCH.STARTED_AT],
                teams = teams
            )
        }

        KIO.ok(ApiResponse.ListDto(result))
    }

    fun getUpcomingCompetitionMatches(
        eventId: UUID,
        limit: Int = 10,
    ): App<Nothing, ApiResponse.ListDto<UpcomingCompetitionMatchInfo>> = KIO.comprehension {
        getUpcomingCompetitionMatches(eventId, limit, !clubShortNames())
    }

    private fun getUpcomingCompetitionMatches(
        eventId: UUID,
        limit: Int,
        clubShortNames: ClubShortNameSettings,
    ): App<Nothing, ApiResponse.ListDto<UpcomingCompetitionMatchInfo>> = KIO.comprehension {

        val matches =
            !CompetitionMatchRepo.getUpcomingMatches(eventId, limit).orDie()

        val real = !toUpcomingCompetitionMatchInfos(matches, clubShortNames)
        // Ohne Nachfrist: CompetitionMatchRepo.getUpcomingMatches nimmt nur Läufe mit
        // START_TIME > jetzt, für die Platzhalter der Kiosk-Ansicht gilt dieselbe Grenze.
        val result = !mergeWithPendingPlaceholders(eventId, real, limit, Duration.ZERO)

        KIO.ok(ApiResponse.ListDto(result))
    }

    // Nur für die Athleten-Anzeige: verspätete und ungeplante Läufe bleiben sichtbar, siehe
    // CompetitionMatchRepo.getUpcomingMatchesForBoard. Die Kiosk-Ansicht nutzt weiterhin
    // getUpcomingCompetitionMatches oben und bleibt davon unberührt.
    fun getUpcomingMatchesForBoard(
        eventId: UUID,
        limit: Int,
    ): App<Nothing, ApiResponse.ListDto<UpcomingCompetitionMatchInfo>> = KIO.comprehension {
        getUpcomingMatchesForBoard(eventId, limit, !clubShortNames())
    }

    private fun getUpcomingMatchesForBoard(
        eventId: UUID,
        limit: Int,
        clubShortNames: ClubShortNameSettings,
    ): App<Nothing, ApiResponse.ListDto<UpcomingCompetitionMatchInfo>> = KIO.comprehension {

        val grace = Duration.ofMinutes(AthleteBoardLogic.DEFAULT_OVERDUE_GRACE_MINUTES.toLong())
        val matches =
            !CompetitionMatchRepo.getUpcomingMatchesForBoard(eventId, limit, grace).orDie()

        val real = !toUpcomingCompetitionMatchInfos(matches, clubShortNames)
        val result = !mergeWithPendingPlaceholders(eventId, real, limit, grace)

        KIO.ok(ApiResponse.ListDto(result))
    }

    /**
     * Mischt Platzhalter aus wartenden Zeitstrahl-Slots (Runde noch nicht erzeugt) unter die
     * echten Läufe, aufsteigend nach Startzeit, gedeckelt auf [limit] - genau wie die Kiosk- und
     * Board-Antworten es ohne Platzhalter schon waren. Die reine Filter-/Mapping-Entscheidung
     * steckt in [AthleteBoardLogic.placeholdersFromPendingSlots] und ist dort ohne Datenbank
     * geprüft; hier passiert nur das Zusammenführen der beiden Quellen.
     *
     * FREE-Slots (Programmpunkte wie "Mittagspause") kommen zusätzlich hinzu, wenn die
     * Veranstaltung das über `Event.showBreaksOnPublicBoards` erlaubt hat (Migration
     * V202608041900) - standardmäßig aus, weil Kiosk und Athleten-Anzeige sparsam bleiben sollen.
     * Die reine Filter-Entscheidung dafür teilt sich dieser Code mit dem Live-Dashboard über
     * [EventScheduleLogic.freeSlotOrNull].
     *
     * [grace] ist dieselbe Nachfrist, mit der die jeweilige Abfrage schon die echten Läufe
     * eingegrenzt hat (Athleten-Anzeige: 30 Minuten, Kiosk: keine). Ohne sie blieben Platzhalter
     * beliebig lange in "nächste Läufe" stehen und verdrängten - der Block ist auf [limit]
     * gedeckelt - die tatsächlich anstehenden Läufe.
     *
     * Hier werden außerdem die ECHTEN Läufe abgesagter Slots MARKIERT
     * ([EventScheduleLogic.skippedMatchIdOrNull]). Die Lauf-Abfragen selbst
     * (`CompetitionMatchRepo.getUpcomingMatches`/`getUpcomingMatchesForBoard`) kennen den
     * Zeitstrahl nicht; solange die Runde nicht gesetzt ist, fängt die Absage schon
     * [EventScheduleLogic.pendingSlotOrNull] ab, danach nur noch diese Stelle. Sie sitzt bewusst
     * hier statt als weitere SQL-Bedingung: die Slots sind für die Platzhalter ohnehin schon
     * gelesen, und beide öffentlichen Ansichten (Kiosk und Athleten-Anzeige) laufen durch genau
     * diese Funktion - eine Regel, ein Ort.
     *
     * Bis zum 07.08.2026 wurden diese Läufe an dieser Stelle still HERAUSGEFILTERT. Für eine
     * Besatzung, die am Steg auf ihren Lauf wartet, ist ein spurlos verschwundener Lauf nicht von
     * einem Anzeigefehler zu unterscheiden - sie sucht weiter. Deshalb bleiben abgesagte Läufe
     * jetzt an ihrer geplanten Stelle stehen, tragen `cancelled = true` und verlieren dabei ihre
     * Mannschaften: auf der Anzeige steht nur noch Wettkampf, Runde, Lauf, die geplante Zeit und
     * "Findet nicht statt". Abgeräumt werden sie von der Nachfrist, mit der die Lauf-Abfrage
     * ohnehin schon eingegrenzt hat ([grace]) - eine zusätzliche Regel braucht es dafür nicht.
     */
    private fun mergeWithPendingPlaceholders(
        eventId: UUID,
        real: List<UpcomingCompetitionMatchInfo>,
        limit: Int,
        grace: Duration,
    ): App<Nothing, List<UpcomingCompetitionMatchInfo>> = KIO.comprehension {
        val showBreaks = !EventRepo.getShowBreaksOnPublicBoards(eventId).orDie()
        val slotRecords = !EventScheduleRepo.getSlots(eventId).orDie()
        val realMatchIds = real.map { it.matchId }.toSet()
        val now = LocalDateTime.now()

        val skippedMatchIds = slotRecords.mapNotNull { r ->
            EventScheduleLogic.skippedMatchIdOrNull(
                setupMatchId = r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH],
                skipped = r[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null,
                roundMaterialized = r.get("round_materialized", Boolean::class.java) == true,
                matchExists = r.get("match_exists", Boolean::class.java) == true,
            )
        }.toSet()
        // Markieren statt filtern: der abgesagte Lauf bleibt an seiner geplanten Stelle stehen,
        // aber ohne Mannschaften - wer am Steg steht, soll seinen Lauf finden und daran ablesen,
        // dass er nicht stattfindet, statt ihn für einen Anzeigefehler zu halten. Die Besatzungen
        // fallen weg, weil an einem abgesagten Lauf keine Aufstellung mehr hängt.
        val upcomingReal = real.map { match ->
            if (match.matchId in skippedMatchIds) {
                match.copy(cancelled = true, teams = emptyList())
            } else {
                match
            }
        }

        fun List<UpcomingCompetitionMatchInfo>.stillUpcoming() =
            filter { AthleteBoardLogic.isStillUpcoming(it.scheduledStartTime, now, grace) }

        // Zwei getrennte Reads — wenn zwischen ihnen eine Runde entsteht oder gelöscht wird,
        // könnte derselbe Lauf doppelt auftauchen; echte Einträge gewinnen.
        val pendingSlots = slotRecords.mapNotNull { r ->
            EventScheduleLogic.pendingSlotOrNull(
                slotId = r[EVENT_SCHEDULE_SLOT.ID]!!,
                setupMatchId = r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH],
                startTime = r[EVENT_SCHEDULE_SLOT.START_TIME]!!,
                competitionId = r.get("competition_id", UUID::class.java),
                competitionName = r.get("competition_name", String::class.java),
                roundName = r.get("round_name", String::class.java),
                matchName = r.get("match_name", String::class.java),
                skipped = r[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null,
                roundMaterialized = r.get("round_materialized", Boolean::class.java) == true,
                matchExists = r.get("match_exists", Boolean::class.java) == true,
            )
        }
        val waitingPlaceholders = AthleteBoardLogic.placeholdersFromPendingSlots(pendingSlots)
            .filterNot { it.matchId in realMatchIds }
            .stillUpcoming()

        val freePlaceholders = if (showBreaks) {
            val freeSlots = slotRecords.mapNotNull { r ->
                EventScheduleLogic.freeSlotOrNull(
                    slotId = r[EVENT_SCHEDULE_SLOT.ID]!!,
                    isFree = r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] == null,
                    name = r[EVENT_SCHEDULE_SLOT.NAME],
                    startTime = r[EVENT_SCHEDULE_SLOT.START_TIME]!!,
                    skipped = r[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null,
                )
            }
            AthleteBoardLogic.placeholdersFromFreeSlots(freeSlots)
                .filterNot { it.matchId in realMatchIds }
                .stillUpcoming()
        } else {
            emptyList()
        }

        KIO.ok(
            AthleteBoardLogic.sortByStartTime(upcomingReal + waitingPlaceholders + freePlaceholders) { it.scheduledStartTime }
                .take(limit)
        )
    }

    // Gemeinsame Abbildung von Roh-Records auf UpcomingCompetitionMatchInfo, genutzt von
    // getUpcomingCompetitionMatches und getUpcomingMatchesForBoard - beide Queries liefern
    // dieselbe Spaltenform.
    private fun toUpcomingCompetitionMatchInfos(
        matches: List<Record>,
        clubShortNames: ClubShortNameSettings,
    ): App<Nothing, List<UpcomingCompetitionMatchInfo>> = KIO.comprehension {
        val result = matches.map { match ->
            val matchId = match[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!
            val teams = !getUpcomingMatchTeams(matchId, clubShortNames)

            UpcomingCompetitionMatchInfo(
                matchId = matchId,
                matchNumber = null, // Could be parsed from match name if needed
                competitionId = match.get("competition_id", UUID::class.java)!!,
                competitionName = match.get("competition_name", String::class.java) ?: "",
                categoryName = match[COMPETITION_VIEW.CATEGORY_NAME],
                scheduledStartTime = match[COMPETITION_MATCH.START_TIME],
                placeName = null, // No place join in this query
                roundNumber = null, // No round number field available
                roundName = match.get("round_name", String::class.java),
                matchName = match.get("match_name", String::class.java),
                executionOrder = match[COMPETITION_SETUP_MATCH.EXECUTION_ORDER] ?: 0,
                teams = teams
            )
        }

        KIO.ok(result)
    }

    fun getRunningMatches(
        eventId: UUID,
        limit: Int = 10,
    ): App<Nothing, ApiResponse.ListDto<RunningMatchInfo>> = KIO.comprehension {
        getRunningMatches(eventId, limit, !clubShortNames())
    }

    private fun getRunningMatches(
        eventId: UUID,
        limit: Int,
        clubShortNames: ClubShortNameSettings,
    ): App<Nothing, ApiResponse.ListDto<RunningMatchInfo>> = KIO.comprehension {

        val matches = !CompetitionMatchRepo.getRunningMatches(eventId, limit).orDie()

        val result = matches.map { match ->
            val matchId = match[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!
            val startTime = match[COMPETITION_MATCH.START_TIME]
            val startedAt = match[COMPETITION_MATCH.STARTED_AT]
            val elapsedMinutes = startedAt?.let {
                java.time.Duration.between(it, LocalDateTime.now()).toMinutes()
            }
            val teams = !getRunningMatchTeams(matchId, clubShortNames)

            RunningMatchInfo(
                matchId = matchId,
                matchNumber = null,
                competitionId = match.get("competition_id", UUID::class.java)!!,
                competitionName = match.get("competition_name", String::class.java) ?: "",
                categoryName = match[COMPETITION_VIEW.CATEGORY_NAME],
                startTime = startTime,
                activatedAt = match[COMPETITION_MATCH.ACTIVATED_AT],
                startedAt = startedAt,
                elapsedMinutes = elapsedMinutes,
                placeName = null,
                roundNumber = null,
                roundName = match.get("round_name", String::class.java),
                matchName = match.get("match_name", String::class.java),
                executionOrder = match[COMPETITION_SETUP_MATCH.EXECUTION_ORDER] ?: 0,
                teams = teams
            )
        }

        KIO.ok(ApiResponse.ListDto(result))
    }

    /**
     * Der Tab „Live" der öffentlichen Ergebnisanzeige: was gerade läuft UND was als nächstes
     * dran ist, jeder Lauf mit seinem Zustand.
     *
     * Zwei bereits erprobte Quellen, keine dritte Abfrage:
     * - [getRunningMatches] liefert die aktivierten Läufe (PREPARING, RUNNING) samt
     *   Teilergebnissen - unverändert das, was dieser Tab schon immer zeigte.
     * - [getUpcomingMatchesForBoard] liefert die anstehenden (UPCOMING, UNSCHEDULED) und bringt
     *   die 30-Minuten-Nachfrist, die Absage-Markierung, die wartenden Runden und die Einstellung
     *   `showBreaksOnPublicBoards` unverändert mit. Eine Regel, ein Ort.
     *
     * Warum ein eigener Endpoint und kein Schalter an `/running-matches`: der bedient auch den
     * Block `running` der Athleten-Anzeige. Anstehende Läufe dort hineinzumischen zerstörte die
     * Blocktrennung, auf der ihre ganze Darstellung aufbaut.
     *
     * Die Ergebnisfreigabe bleibt unberührt, und zwar ohne zusätzliche Prüfung: die zweite
     * Abfrage schließt beendete und vollständig gewertete Läufe per SQL aus und liefert
     * Mannschaften ohne Platz und ohne Zeit. Ein Lauf, den `PublicResultsVisibility` zurückhalten
     * soll, kann hier gar nicht entstehen - der Schutz sitzt in dieser SQL-Auswahl
     * (`CompetitionMatchRepo.getUpcomingMatchesForBoard`) und im ergebnisfeldlosen
     * `UpcomingMatchTeamInfo`. Der Filter in [LiveMatchesLogic.merge] ist nur noch eine
     * Zusicherung über das Ergebnis, kein zusätzlicher Riegel (siehe dortiges KDoc).
     *
     * Beide Quellen bekommen [limit] einzeln; gedeckelt wird erst nach dem Zusammenführen.
     */
    fun getLiveMatches(
        eventId: UUID,
        limit: Int,
    ): App<Nothing, ApiResponse.ListDto<LiveMatchInfo>> = KIO.comprehension {
        val now = LocalDateTime.now()
        val key = eventId to limit

        // Anders als beim Athleten-Board gibt es hier kein je Antwort frisches Feld wie
        // serverTime - der zwischengespeicherte Stand kann unverändert zurückgehen.
        val cached = liveMatchesCache[key]
            ?.takeIf { AthleteBoardLogic.isCacheFresh(it.builtAt, now) }

        if (cached != null) {
            KIO.ok(cached.dto)
        } else {
            // Einmal je Aufbau, nicht je Mannschaft - beide Blöcke lösen zusammen leicht hundert
            // Vereinsnamen auf, und dieser Endpoint läuft im Viertelminutentakt.
            val clubShortNames = !clubShortNames()

            val activated = !getRunningMatches(eventId, limit, clubShortNames)
            val upcoming = !getUpcomingMatchesForBoard(eventId, limit, clubShortNames)

            val dto = ApiResponse.ListDto(
                LiveMatchesLogic.merge(
                    activated = activated.data.map { it.toLiveMatchInfo() },
                    upcoming = upcoming.data.map { it.toLiveMatchInfo() },
                    limit = limit,
                )
            )

            // Laufen mehrere Abrufe gleichzeitig in dieses Fenster, rechnen sie doppelt und der
            // letzte gewinnt - bei Millisekunden Rechenzeit je Eintrag kein Grund für ein Lock.
            liveMatchesCache[key] = CachedLiveMatches(now, dto)

            KIO.ok(dto)
        }
    }

    fun getAthleteBoard(eventId: UUID): App<EventInfoProblem, ApiResponse.Dto<AthleteBoardDto>> =
        KIO.comprehension {
            val now = LocalDateTime.now()

            // serverTime ist die Bezugsgröße für den Countdown auf dem Gerät und muss
            // deshalb je Antwort frisch sein — nur der Rest der Antwort kommt aus dem
            // Zwischenspeicher. Die startState-Felder darin sind höchstens
            // CACHE_TTL_SECONDS alt; das trägt die Anzeige.
            val cached = athleteBoardCache[eventId]
                ?.takeIf { AthleteBoardLogic.isCacheFresh(it.builtAt, now) }

            if (cached != null) {
                KIO.ok(ApiResponse.Dto(cached.dto.copy(serverTime = now)))
            } else {
                val eventName = !EventRepo.getName(eventId).orDie()
                if (eventName == null) {
                    !KIO.fail<EventInfoProblem>(EventInfoProblem.EventNotFound(eventId))
                }

                // findByEvent liefert nur aktive Zeilen, aufsteigend nach sort_order.
                // Gedacht ist genau eine ATHLETE_BOARD-Zeile; bei mehreren gewinnt die erste.
                val views = !InfoViewConfigurationRepo.findByEvent(eventId).orDie()
                val boardView = views.firstOrNull { it.viewType == InfoViewType.ATHLETE_BOARD }

                val config = AthleteBoardLogic.resolveConfig(
                    filters = boardView?.filters?.let { objectMapper.readTree(it.data()) },
                    displayDurationSeconds = boardView?.displayDurationSeconds,
                )

                // Einmal je Aufbau, nicht je Mannschaft: die Anzeige pollt, und die drei Blöcke
                // darunter lösen zusammen leicht hundert Vereinsnamen auf.
                val clubShortNames = !clubShortNames()

                val running = !getRunningMatches(eventId, config.runningLimit, clubShortNames)
                val upcoming = !getUpcomingMatchesForBoard(eventId, config.upcomingLimit, clubShortNames)
                val results = !getLatestMatchResults(eventId, config.resultsLimit, null, clubShortNames)

                val dto = AthleteBoardDto(
                    eventName = eventName!!,
                    serverTime = now,
                    refreshIntervalSeconds = config.refreshIntervalSeconds,
                    showCountdown = config.showCountdown,
                    running = running.data.map {
                        it.toAthleteBoardMatch(now, config.showCountdown)
                    },
                    upcoming = AthleteBoardLogic.sortByStartTime(
                        upcoming.data.map { it.toAthleteBoardMatch(now, config.showCountdown) }
                    ) { it.startTime },
                    results = results.data.map { it.toAthleteBoardResult() },
                )

                // Laufen mehrere Abrufe gleichzeitig in dieses Fenster, rechnen sie doppelt
                // und der letzte gewinnt — bei Millisekunden Rechenzeit je Eintrag kein
                // Grund für ein Lock.
                athleteBoardCache[eventId] = CachedBoard(now, dto)

                KIO.ok(ApiResponse.Dto(dto))
            }
        }


    // Helper Methods

    /**
     * Die gepflegten Vereinskurzformen und die Kürzungsregeln, EINMAL je Abruf. Aufgelöst wird danach ohne weitere
     * Abfrage - die öffentlichen Endpoints hier laufen im Sekunden- bis Viertelminutentakt und
     * bauen je Antwort Dutzende Mannschaften auf; ein Nachschlagen je Boot wäre derselbe Fehler
     * in klein, gegen den der Zwischenspeicher der Athleten-Anzeige gebaut wurde.
     */
    private fun clubShortNames(): App<Nothing, ClubShortNameSettings> =
        ClubShortNameSettings.load()

    /**
     * Die Vereinskette einer Mannschaft aus den Zeilen ihrer Crew - in Bootsreihenfolge, wie die
     * Abfrage sie liefert. Erwartet die Spalten der drei Anzeige-Abfragen in
     * [CompetitionMatchTeamRepo] (inkl. des zweiten, aliasierten CLUB-Joins auf die Person).
     */
    private fun clubComposition(
        records: List<Record>,
        clubShortNames: ClubShortNameSettings,
    ): ClubComposition = ClubComposition.of(
        records.map {
            ClubComposition.clubWorn(
                external = it[PARTICIPANT.EXTERNAL],
                externalClubName = it[PARTICIPANT.EXTERNAL_CLUB_NAME],
                ownClubName = it.get(CompetitionMatchTeamRepo.PARTICIPANT_CLUB_NAME, String::class.java),
            )
        },
        clubShortNames,
    )

    /**
     * Die erfasste Zeit als Anzeigetext, oder null solange keine Zeit vorliegt. Erwartet die
     * TIMECODE-Spalten im Record (left join) - Ergebnis- und Laufabfrage liefern beide dieselbe
     * Spaltenform. [precision] kommt pro Lauf aus [Timecode.displayPrecision], damit alle Zeiten
     * eines Laufs einheitlich und so grob wie möglich angezeigt werden.
     */
    private fun timeStringOrNull(record: Record, precision: Timecode.MillisecondPrecision): String? =
        record[TIMECODE.TIME]?.let {
            Timecode(
                millis = it,
                baseUnit = Timecode.BaseUnit.valueOf(record[TIMECODE.BASE_UNIT]!!),
                millisecondPrecision = precision,
            ).toString()
        }

    private fun getMatchResultTeams(
        matchId: UUID,
        clubShortNames: ClubShortNameSettings,
    ): App<Nothing, List<MatchResultTeamInfo>> = KIO.comprehension {
        val records = !CompetitionMatchTeamRepo.getTeamsForMatchResult(matchId).orDie()
        val timePrecision = Timecode.displayPrecision(records.mapNotNull { it[TIMECODE.TIME] })

        val result = records.groupBy { it[COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION] }
            .map { (registrationId, groupedRecords) ->
                val first = groupedRecords.first()
                val clubs = clubComposition(groupedRecords, clubShortNames)
                MatchResultTeamInfo(
                    teamId = registrationId!!,
                    teamName = first.get("team_name", String::class.java),
                    teamNumber = first[COMPETITION_REGISTRATION.TEAM_NUMBER],
                    clubName = first.get("club_name", String::class.java),
                    clubsShort = clubs.short.ifEmpty { null },
                    clubsFull = clubs.full.ifEmpty { null },
                    startNumber = first[COMPETITION_MATCH_TEAM.START_NUMBER]!!,
                    place = first[COMPETITION_MATCH_TEAM.PLACE],
                    timeString = timeStringOrNull(first, timePrecision),
                    failed = first[COMPETITION_MATCH_TEAM.FAILED] == true,
                    failedReason = first[COMPETITION_MATCH_TEAM.FAILED_REASON],
                    penaltySeconds = first[COMPETITION_MATCH_TEAM.PENALTY_SECONDS],
                    penaltyNote = first[COMPETITION_MATCH_TEAM.PENALTY_NOTE],
                    deregistered = first.get("deregistered", Boolean::class.java),
                    deregisteredReason = first.get("deregistration_reason", String::class.java),
                    participants = groupedRecords.mapNotNull { record ->
                        record.get("participant_id", UUID::class.java)?.let {
                            ParticipantInfo(
                                participantId = it,
                                firstName = record[PARTICIPANT.FIRSTNAME] ?: "",
                                lastName = record[PARTICIPANT.LASTNAME] ?: "",
                                namedRole = record.get("named_role", String::class.java),
                                externalClubName = record[PARTICIPANT.EXTERNAL_CLUB_NAME]
                            )
                        }
                    }
                )
            }

        KIO.ok(result)
    }

    private fun getUpcomingMatchTeams(
        matchId: UUID,
        clubShortNames: ClubShortNameSettings,
    ): App<Nothing, List<UpcomingMatchTeamInfo>> = KIO.comprehension {
        val records = !CompetitionMatchTeamRepo.getTeamsForUpcomingMatch(matchId).orDie()

        val result = records.groupBy { it[COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION] }
            .map { (registrationId, groupedRecords) ->
                val first = groupedRecords.first()
                val clubs = clubComposition(groupedRecords, clubShortNames)
                UpcomingMatchTeamInfo(
                    teamId = registrationId!!,
                    teamName = first.get("team_name", String::class.java),
                    teamNumber = first[COMPETITION_REGISTRATION.TEAM_NUMBER],
                    startNumber = first[COMPETITION_MATCH_TEAM.START_NUMBER],
                    clubName = first.get("club_name", String::class.java),
                    clubsShort = clubs.short.ifEmpty { null },
                    clubsFull = clubs.full.ifEmpty { null },
                    participants = groupedRecords.mapNotNull { record ->
                        record.get("participant_id", UUID::class.java)?.let {
                            UpcomingMatchParticipantInfo(
                                participantId = it,
                                firstName = record[PARTICIPANT.FIRSTNAME] ?: "",
                                lastName = record[PARTICIPANT.LASTNAME] ?: "",
                                namedRole = record.get("named_role", String::class.java),
                                year = record[PARTICIPANT.YEAR],
                                gender = record[PARTICIPANT.GENDER]?.name,
                                externalClubName = record[PARTICIPANT.EXTERNAL_CLUB_NAME]
                            )
                        }
                    }
                )
            }

        KIO.ok(result)
    }

    private fun getRunningMatchTeams(
        matchId: UUID,
        clubShortNames: ClubShortNameSettings,
    ): App<Nothing, List<RunningMatchTeamInfo>> = KIO.comprehension {
        val records = !CompetitionMatchTeamRepo.getTeamForRunningMatch(matchId).orDie()
        val timePrecision = Timecode.displayPrecision(records.mapNotNull { it[TIMECODE.TIME] })

        val result = records.groupBy { it[COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION] }
            .map { (registrationId, groupedRecords) ->
                val first = groupedRecords.first()
                val clubs = clubComposition(groupedRecords, clubShortNames)
                RunningMatchTeamInfo(
                    teamId = registrationId!!,
                    teamName = first.get("team_name", String::class.java),
                    teamNumber = first[COMPETITION_REGISTRATION.TEAM_NUMBER],
                    startNumber = first[COMPETITION_MATCH_TEAM.START_NUMBER],
                    clubName = first.get("club_name", String::class.java),
                    clubsShort = clubs.short.ifEmpty { null },
                    clubsFull = clubs.full.ifEmpty { null },
                    currentScore = null, // Could be calculated if scoring data is available
                    currentPosition = first[COMPETITION_MATCH_TEAM.PLACE],
                    timeString = timeStringOrNull(first, timePrecision),
                    penaltySeconds = first[COMPETITION_MATCH_TEAM.PENALTY_SECONDS],
                    penaltyNote = first[COMPETITION_MATCH_TEAM.PENALTY_NOTE],
                    failed = first[COMPETITION_MATCH_TEAM.FAILED] == true,
                    failedReason = first[COMPETITION_MATCH_TEAM.FAILED_REASON],
                    participants = groupedRecords.mapNotNull { record ->
                        record.get("participant_id", UUID::class.java)?.let {
                            UpcomingMatchParticipantInfo(
                                participantId = it,
                                firstName = record[PARTICIPANT.FIRSTNAME] ?: "",
                                lastName = record[PARTICIPANT.LASTNAME] ?: "",
                                namedRole = record.get("named_role", String::class.java),
                                year = record[PARTICIPANT.YEAR],
                                gender = record[PARTICIPANT.GENDER]?.name,
                                externalClubName = record[PARTICIPANT.EXTERNAL_CLUB_NAME]
                            )
                        }
                    }
                )
            }

        KIO.ok(result)
    }

}