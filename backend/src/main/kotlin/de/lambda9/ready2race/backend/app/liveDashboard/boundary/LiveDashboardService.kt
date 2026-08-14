package de.lambda9.ready2race.backend.app.liveDashboard.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.club.boundary.ClubComposition
import de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameLogic
import de.lambda9.ready2race.backend.app.club.boundary.ClubShortNameSettings
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.AutoRoundProgressionLogic
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.AutoRoundProgressionService
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.ChainProgressionMode
import de.lambda9.ready2race.backend.app.eventInfo.boundary.EventChangeMarker
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.EventScheduleLogic
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ScheduleChainService
import de.lambda9.ready2race.backend.app.eventSchedule.control.EventScheduleRepo
import de.lambda9.ready2race.backend.app.liveDashboard.control.CheckSeverityRepo
import de.lambda9.ready2race.backend.app.liveDashboard.control.LiveDashboardRepo
import de.lambda9.ready2race.backend.app.liveDashboard.control.MatchTeamNoteRepo
import de.lambda9.ready2race.backend.app.liveDashboard.entity.*
import de.lambda9.ready2race.backend.app.matchStatus.boundary.MatchByeService
import de.lambda9.ready2race.backend.app.participantTracking.control.ParticipantTrackingRepo
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.app.substitution.entity.ParticipantForExecutionDto
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamLapRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.ratingcategory.boundary.RatingCategoryRanking
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRef
import de.lambda9.ready2race.backend.data.Timecode
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionCheckSeverityRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.MatchTeamNoteRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import org.jooq.Record
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

object LiveDashboardService {

    /**
     * [crew] schaltet die dritte Anzeigestufe der Karte frei (Nachname, Vereinskurzform und Rolle
     * je Person). Sie hängt an der Fensterbreite, nicht am Gerät: am Telefon bleibt die Nutzlast
     * des Sekunden-Polls unverändert, am Laptop kommen grob 5 KB gzip je Abruf dazu.
     */
    fun getLiveDashboard(
        eventId: UUID,
        scope: LiveDashboardScope,
        crew: Boolean = false,
    ): App<LiveDashboardError, ApiResponse.ETagged<LiveDashboardDto>> =
        KIO.comprehension {
            val exists = !EventRepo.exists(eventId).orDie()
            if (!exists) {
                return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
            }

            val matchRecords = !LiveDashboardRepo.getMatches(eventId).orDie()
            val teamRecords = !LiveDashboardRepo.getTeams(eventId).orDie()
            val requirementRecords = !LiveDashboardRepo.getEventRequirements(eventId).orDie()
            val checkRecords = !LiveDashboardRepo.getChecks(eventId).orDie()
            val invoiceRecords = !LiveDashboardRepo.getInvoicePaymentsByClub(eventId).orDie()
            val substitutionRecords = !SubstitutionRepo.getByEvent(eventId, null, Privilege.Scope.GLOBAL).orDie()
            // Letzter Steg-Scan je Person: Grundlage für "Boot ist in der Arena" pro Team.
            val lastScanByParticipant = !ParticipantTrackingRepo.getScansByEvent(eventId).orDie()
                .map { scans ->
                    scans.groupBy { it[PARTICIPANT_TRACKING.PARTICIPANT]!! }
                        .mapValues { (_, rows) ->
                            val last = rows.maxBy { it[PARTICIPANT_TRACKING.SCANNED_AT]!! }
                            last[PARTICIPANT_TRACKING.SCAN_TYPE]!! to last[PARTICIPANT_TRACKING.SCANNED_AT]!!
                        }
                }
            // Einmal gelesen und zweifach genutzt: für die Platzhalter (getPendingSlots) und für die
            // Absagen, die an echten Läufen hängen. Zwei Reads würden hier auseinanderlaufen können.
            val slotRecords = !EventScheduleRepo.getSlots(eventId).orDie()
            val byeByMatch = !MatchByeService.byeByMatch(eventId)

            val skippedMatchIds = slotRecords.mapNotNull { r ->
                EventScheduleLogic.skippedMatchIdOrNull(
                    setupMatchId = r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH],
                    skipped = r[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null,
                    roundMaterialized = r.get("round_materialized", Boolean::class.java) == true,
                    matchExists = r.get("match_exists", Boolean::class.java) == true,
                )
            }.toSet()

            // Abweichende Schweregrade der Veranstaltung; ohne Eintrag greift der Standard in
            // LiveDashboardLogic.defaultSeverity. Unbekannte Zeilen werden übergangen statt zu
            // scheitern, siehe LiveDashboardLogic.buildCheckSeverityConfig.
            val severityConfig = LiveDashboardLogic.buildCheckSeverityConfig(
                !CheckSeverityRepo.getByEvent(eventId).orDie().map { rows ->
                    rows.map {
                        Triple(
                            it[COMPETITION_CHECK_SEVERITY.COMPETITION]!!,
                            it[COMPETITION_CHECK_SEVERITY.CHECK_TYPE]!! to
                                it[COMPETITION_CHECK_SEVERITY.PARTICIPANT_REQUIREMENT],
                            it[COMPETITION_CHECK_SEVERITY.SEVERITY]!!,
                        )
                    }
                }
            )

            // Beides einmal je Abruf, nicht je Mannschaft: der Endpunkt wird im Sekundentakt
            // gepollt, und eine Veranstaltung hat leicht hundert Mannschaften. Die Einstellungen
            // tragen gepflegte Kurzformen und Kürzungsregeln zusammen - ein Ladeweg, nicht zwei.
            val clubShortNames = !ClubShortNameSettings.load()
            val wornClubs = !wornClubsByParticipant(teamRecords, substitutionRecords)

            val context =
                ParticipantContext(requirementRecords, checkRecords, substitutionRecords, severityConfig, wornClubs)

            val paidAtsByClub = invoiceRecords.groupBy(
                { it[INVOICE_FOR_EVENT_REGISTRATION.CLUB] },
                { it[INVOICE_FOR_EVENT_REGISTRATION.PAID_AT] },
            )

            val teamsByMatch = teamRecords.groupBy { it.get("match_id", UUID::class.java)!! }
            val now = LocalDateTime.now()

            // Resolves one team's registered crew against the round's substitutions and builds its DTO.
            fun buildTeamDto(
                registrationId: UUID,
                rows: List<Record>,
                startTime: LocalDateTime?,
                timePrecision: Timecode.MillisecondPrecision,
                matchRunning: Boolean,
            ): App<Nothing, LiveDashboardTeamDto> = KIO.comprehension {
                val first = rows.first()
                val clubId = first.get("club_id", UUID::class.java)
                val clubName = first.get("club_name", String::class.java)
                val teamName = first.get("team_name", String::class.java)
                val competitionId = first.get("competition_id", UUID::class.java)!!
                val checkInOutRequired = first[COMPETITION_PROPERTIES.CHECK_IN_OUT_REQUIRED] == true
                val deregistered = first.get("deregistered", Boolean::class.java) == true
                val invoiceState = LiveDashboardLogic.deriveInvoiceState(
                    clubId?.let { paidAtsByClub[it] } ?: emptyList()
                )

                val participants = !buildParticipants(rows, registrationId, startTime, context, competitionId)

                val inArenaAt = LiveDashboardLogic.teamInArenaAt(
                    participants.map { lastScanByParticipant[it.participantId] }
                )
                val invoiceSeverity = LiveDashboardLogic.invoiceSeverity(
                    invoiceState,
                    severityConfig.severityFor(competitionId, CheckType.INVOICE_OPEN),
                )
                val inArenaSeverity = LiveDashboardLogic.inArenaSeverity(
                    evaluated = LiveDashboardLogic.inArenaApplies(
                        matchRunning = matchRunning,
                        checkInOutRequired = checkInOutRequired,
                        deregistered = deregistered,
                    ),
                    inArena = inArenaAt != null,
                    configured = severityConfig.severityFor(competitionId, CheckType.NOT_IN_ARENA),
                )

                // Die Kette entsteht aus der Crew, die wirklich startet - nach den Ummeldungen.
                // Genau das ist der Fall, der im Betrieb weh tut: kommt die Ersatzperson aus einem
                // anderen Verein, steht das ab sofort auf der Karte.
                val clubs = ClubComposition.of(participants.map { it.clubName }, clubShortNames)

                KIO.ok(
                    LiveDashboardTeamDto(
                        teamId = registrationId,
                        teamName = teamName,
                        clubName = clubName,
                        clubsShort = clubs.short,
                        clubsFull = clubs.full,
                        crew = if (crew) {
                            participants.map {
                                LiveDashboardCrewMemberDto(
                                    lastName = it.lastName,
                                    clubShort = it.clubName?.let { name -> ClubShortNameLogic.shorten(name, clubShortNames) },
                                    role = LiveDashboardLogic.roleAbbreviation(it.namedRole),
                                )
                            }
                        } else {
                            null
                        },
                        startNumber = first[COMPETITION_MATCH_TEAM.START_NUMBER],
                        place = first[COMPETITION_MATCH_TEAM.PLACE],
                        ratingCategory = first.get(CompetitionMatchTeamRepo.RATING_CATEGORY_ID, UUID::class.java)
                            ?.let { categoryId ->
                                RatingCategoryRef(
                                    id = categoryId,
                                    name = first.get(
                                        CompetitionMatchTeamRepo.RATING_CATEGORY_NAME,
                                        String::class.java
                                    ) ?: "",
                                    sortOrder = first.get(
                                        CompetitionMatchTeamRepo.RATING_CATEGORY_SORT_ORDER,
                                        Int::class.java
                                    ) ?: RatingCategoryRef.UNCONFIGURED_SORT_ORDER,
                                )
                            },
                        // Erst wenn das ganze Feld des Laufs steht, siehe buildMatchDto.
                        categoryPlace = null,
                        time = first[TIMECODE.TIME]?.let {
                            Timecode(
                                millis = it,
                                baseUnit = Timecode.BaseUnit.valueOf(first[TIMECODE.BASE_UNIT]!!),
                                millisecondPrecision = timePrecision,
                            ).toString()
                        },
                        startedAt = first.get("team_started_at", LocalDateTime::class.java),
                        failed = first[COMPETITION_MATCH_TEAM.FAILED] == true,
                        failedReason = first[COMPETITION_MATCH_TEAM.FAILED_REASON],
                        penaltySeconds = first[COMPETITION_MATCH_TEAM.PENALTY_SECONDS],
                        penaltyNote = first[COMPETITION_MATCH_TEAM.PENALTY_NOTE],
                        deregistered = deregistered,
                        deregisteredReason = first.get("deregistration_reason", String::class.java),
                        invoiceState = invoiceState,
                        // Die Personendaten selbst bleiben hier: sie sind der größte Posten im
                        // Poll und werden erst im Detail-Dialog gebraucht - nur die fertige Ampel
                        // je Bedingung fließt in die Team-Ampel ein.
                        inArenaRequired = checkInOutRequired,
                        invoiceSeverity = invoiceSeverity,
                        inArenaSeverity = inArenaSeverity,
                        severity = LiveDashboardLogic.teamSeverity(
                            requirementSeverities = participants.flatMap { it.requirements }.map { it.severity },
                            invoice = invoiceSeverity,
                            inArena = inArenaSeverity,
                        ),
                        substituted = participants.any { it.substitutedFor != null },
                        inArenaAt = inArenaAt,
                    )
                )
            }

            fun buildMatchDto(match: Record): App<Nothing, LiveDashboardMatchDto> = KIO.comprehension {
                val matchId = match[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!
                val startTime = match[COMPETITION_MATCH.START_TIME]
                val startedAt = match[COMPETITION_MATCH.STARTED_AT]
                val finishedAt = match[COMPETITION_MATCH.FINISHED_AT]
                val activatedAt = match[COMPETITION_MATCH.ACTIVATED_AT]
                // Für die Arena-Prüfung zählt weiterhin die Aktivierung, nicht der Ist-Start: ein
                // Boot, das an den Start gerufen ist, gehört raus - unabhängig davon, ob das Rennen
                // schon unterwegs ist.
                val running = activatedAt != null

                val matchRows = teamsByMatch[matchId] ?: emptyList()
                // Anzeige-Präzision pro Lauf: standardmäßig eine Nachkommastelle, feiner nur,
                // wenn sonst unterschiedliche Zeiten gleich aussähen.
                val timePrecision = Timecode.displayPrecision(matchRows.mapNotNull { it[TIMECODE.TIME] })
                val teams = !matchRows
                    .groupBy { it[COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION]!! }
                    .toList()
                    .traverse { (registrationId, rows) -> buildTeamDto(registrationId, rows, startTime, timePrecision, running) }
                    // Die Karte bleibt nach Bahn sortiert - am Steg wird sie gegen das Wasser
                    // gelesen, nicht gegen die Ergebnisliste. Nur der Kategorieplatz kommt hinzu,
                    // damit das Ergebnis eines beendeten Laufs dieselbe Zahl zeigt wie die
                    // oeffentliche Ansicht.
                    .map { list -> withCategoryPlaces(list).sortedWith(compareBy(nullsLast()) { it.startNumber }) }

                KIO.ok(
                    LiveDashboardMatchDto(
                        matchId = matchId,
                        state = LiveDashboardLogic.deriveMatchState(
                            activatedAt = activatedAt,
                            startedAt = startedAt,
                            startTime = startTime,
                            finishedAt = finishedAt,
                            // "Erledigt", nicht "gefahren": der Zustand fragt, ob noch jemand auf
                            // ein Ergebnis wartet - eine Abmeldung zählt dabei mit.
                            teamResults = teams.map { LiveDashboardLogic.teamIsSettled(it.place, it.failed, it.deregistered) },
                            skipped = matchId in skippedMatchIds,
                        ),
                        bye = byeByMatch[matchId],
                        competitionId = match.get("competition_id", UUID::class.java)!!,
                        competitionName = match.get("competition_name", String::class.java) ?: "",
                        competitionIdentifier = match.get("competition_identifier", String::class.java),
                        competitionShortName = match.get("competition_short_name", String::class.java),
                        categoryName = match[COMPETITION_VIEW.CATEGORY_NAME],
                        roundName = match.get("round_name", String::class.java),
                        matchName = match.get("match_name", String::class.java),
                        executionOrder = match[COMPETITION_SETUP_MATCH.EXECUTION_ORDER] ?: 0,
                        startTime = startTime,
                        startedAt = startedAt,
                        elapsedMinutes = startedAt?.let { Duration.between(it, now).toMinutes().coerceAtLeast(0) },
                        teams = teams,
                        raceClockerPollError = match[COMPETITION_MATCH.RACECLOCKER_POLL_ERROR],
                        raceClockerAutoPausedAt = match[COMPETITION_MATCH.RACECLOCKER_AUTO_PAUSED_AT],
                        // Die Sichtbarkeitsregel steht gemeinsam mit der Durchführungs-Ansicht
                        // (Conversions.kt) in AutoRoundProgressionLogic.visibleRecalculationNotice,
                        // damit sie nicht in zwei Oberflächen auseinanderlaufen kann.
                        pairingsRecalculatedAt = AutoRoundProgressionLogic.visibleRecalculationNotice(
                            pairingsRecalculatedAt = match[COMPETITION_MATCH.PAIRINGS_RECALCULATED_AT],
                            activatedAt = activatedAt,
                            startedAt = startedAt,
                            finishedAt = finishedAt,
                        ),
                    )
                )
            }

            val matches = !matchRecords.traverse { match -> buildMatchDto(match) }

            // Zwischenzeiten und Notizen je Boot nachtragen - je eine Abfrage für alle Läufe des
            // Dashboards, danach je Lauf/Meldung zugeordnet. Erst hier, nicht in buildTeamDto,
            // damit es nicht je Boot eine eigene Abfrage wird.
            val matchIds = matches.map { it.matchId }.toSet()
            val lapsByTeam = !CompetitionMatchTeamLapRepo.getByMatches(matchIds).orDie()
            val notesByTeam = !MatchTeamNoteRepo.getByMatches(matchIds).orDie()
            val matchesWithLaps = if (lapsByTeam.isEmpty() && notesByTeam.isEmpty()) matches else matches.map { m ->
                m.copy(teams = m.teams.map { t ->
                    t.copy(
                        laps = lapsByTeam[m.matchId to t.teamId] ?: emptyList(),
                        notes = notesByTeam[m.matchId to t.teamId] ?: emptyList(),
                    )
                })
            }

            val pendingSlots = getPendingSlots(slotRecords, matchesWithLaps.map { it.matchId }.toSet())
            val chainProgressionMode = !EventRepo.getChainProgressionMode(eventId).orDie()
            val notice = !EventRepo.getNotice(eventId).orDie()

            KIO.ok(
                ApiResponse.ETagged(
                    LiveDashboardDto(
                        matches = LiveDashboardLogic.selectForScope(matchesWithLaps, scope),
                        // Unabhängig vom Scope: auch im LIVE-Ausschnitt soll sichtbar bleiben, was
                        // als nächstes ansteht, auch wenn die Runde noch nicht erzeugt ist.
                        pendingSlots = pendingSlots,
                        chainProgressionMode = chainProgressionMode,
                        notice = notice,
                    )
                )
            )
        }

    /**
     * WAITING- und FREE-Slots des Events als Platzhalter (Task 14, erweitert um Programmpunkte) -
     * gemeinsam nach Startzeit sortiert. Die "nur WAITING zählt"-Regel für Lauf-Platzhalter steckt
     * gemeinsam mit der Athleten-Anzeige in [EventScheduleLogic.pendingSlotOrNull]: SKIPPED, FREE,
     * LINKED und OBSOLETE liefern dort keinen Eintrag - LINKED ist bereits ein echter Lauf und
     * steckt in [matches], die anderen sind kein Kandidat für einen künftigen Lauf. FREE-Slots
     * (Programmpunkte) kommen zusätzlich über [EventScheduleLogic.freeSlotOrNull] hinzu - anders
     * als bei der Athleten-Anzeige/dem Kiosk ist das hier gewollt: Schiedsrichter sollen auch
     * Pausen im Ablauf sehen, öffentliche Boards bewusst nicht.
     *
     * [slotRecords] kommt von außen, weil dieselben Zeilen im Aufrufer bereits für die Absagen an
     * echten Läufen gebraucht werden.
     */
    /**
     * Setzt je Mannschaft den Platz innerhalb ihrer Wertungskategorie. Gerechnet wird in
     * [RatingCategoryRanking] - derselben Stelle, aus der die öffentliche Ergebnisseite und die
     * Athleten-Anzeige ihre Zahlen beziehen. Die Reihenfolge der Liste bleibt Sache des Aufrufers.
     */
    private fun withCategoryPlaces(teams: List<LiveDashboardTeamDto>): List<LiveDashboardTeamDto> =
        RatingCategoryRanking.groupAndRank(
            items = teams,
            category = { it.ratingCategory },
            place = { it.place },
            tieBreak = { it.startNumber ?: Int.MAX_VALUE },
        ).flatMap { section ->
            section.entries.map { it.item.copy(categoryPlace = it.categoryPlace) }
        }

    private fun getPendingSlots(slotRecords: List<Record>, matchIds: Set<UUID>): List<PendingSlotDto> {
        // Die Rohzeile bleibt neben dem Platzhalter stehen: Rennnummer und Kurzname braucht nur das
        // Dashboard, und PendingScheduleSlotInfo teilt sich die Athleten-Anzeige, die ohne sie
        // auskommt.
        val waiting = slotRecords.mapNotNull { r ->
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
            )?.let { r to it }
        }
            // Zwei getrennte Reads — wenn zwischen ihnen eine Runde entsteht oder gelöscht wird,
            // könnte derselbe Lauf doppelt auftauchen; echte Einträge gewinnen.
            .filterNot { (_, slot) -> slot.setupMatchId in matchIds }
            .map { (r, slot) ->
                PendingSlotDto(
                    slotId = slot.slotId,
                    startTime = slot.startTime,
                    name = null,
                    competitionName = slot.competitionName,
                    competitionIdentifier = r.get("competition_identifier", String::class.java),
                    competitionShortName = r.get("competition_short_name", String::class.java),
                    roundName = slot.roundName,
                    matchName = slot.matchName,
                )
            }

        val free = slotRecords.mapNotNull { r ->
            EventScheduleLogic.freeSlotOrNull(
                slotId = r[EVENT_SCHEDULE_SLOT.ID]!!,
                isFree = r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] == null,
                name = r[EVENT_SCHEDULE_SLOT.NAME],
                startTime = r[EVENT_SCHEDULE_SLOT.START_TIME]!!,
                skipped = r[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null,
            )
        }.map { slot ->
            PendingSlotDto(
                slotId = slot.slotId,
                startTime = slot.startTime,
                name = slot.name,
                competitionName = null,
                competitionIdentifier = null,
                competitionShortName = null,
                roundName = null,
                matchName = null,
            )
        }

        return (waiting + free).sortedBy { it.startTime }
    }

    /**
     * Personendaten einer Mannschaft für den Detail-Dialog: Aufstellung, Ummeldungen und die
     * Teilnahmebedingungen mit allem, was die Liste bewusst nicht mehr mitschickt.
     */
    fun getTeamDetail(
        eventId: UUID,
        matchId: UUID,
        teamId: UUID,
    ): App<LiveDashboardError, ApiResponse.Dto<LiveDashboardTeamDetailDto>> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        val teamRecords = !LiveDashboardRepo.getTeams(eventId, matchId, teamId).orDie()
        if (teamRecords.isEmpty()) {
            return@comprehension KIO.fail(LiveDashboardError.TeamNotFound(teamId))
        }

        val startTime = !LiveDashboardRepo.getMatchStartTime(matchId).orDie()
        val requirementRecords = !LiveDashboardRepo.getEventRequirements(eventId).orDie()
        val checkRecords = !LiveDashboardRepo.getChecks(eventId).orDie()
        val substitutionRecords = !SubstitutionRepo.getByEvent(eventId, null, Privilege.Scope.GLOBAL).orDie()
        val severityConfig = LiveDashboardLogic.buildCheckSeverityConfig(
            !CheckSeverityRepo.getByEvent(eventId).orDie().map { rows ->
                rows.map {
                    Triple(
                        it[COMPETITION_CHECK_SEVERITY.COMPETITION]!!,
                        it[COMPETITION_CHECK_SEVERITY.CHECK_TYPE]!! to
                            it[COMPETITION_CHECK_SEVERITY.PARTICIPANT_REQUIREMENT],
                        it[COMPETITION_CHECK_SEVERITY.SEVERITY]!!,
                    )
                }
            }
        )
        val competitionId = teamRecords.first().get("competition_id", UUID::class.java)!!

        // Nur der Detail-Dialog zeigt den Steg-Scan je Person; die Übersichtskarten hängen am
        // Sekunden-Takt und fassen ihn ohnehin je Boot zusammen.
        val lastScanByParticipant = !ParticipantTrackingRepo.getScansByEvent(eventId).orDie()
            .map { scans ->
                scans.groupBy { it[PARTICIPANT_TRACKING.PARTICIPANT]!! }
                    .mapValues { (_, rows) ->
                        val last = rows.maxBy { it[PARTICIPANT_TRACKING.SCANNED_AT]!! }
                        last[PARTICIPANT_TRACKING.SCAN_TYPE]!! to last[PARTICIPANT_TRACKING.SCANNED_AT]!!
                    }
            }

        val participants = !buildParticipants(
            rows = teamRecords,
            registrationId = teamId,
            startTime = startTime,
            context = ParticipantContext(
                requirementRecords,
                checkRecords,
                substitutionRecords,
                severityConfig,
                !wornClubsByParticipant(teamRecords, substitutionRecords),
                lastScanByParticipant,
            ),
            competitionId = competitionId,
        )

        KIO.ok(ApiResponse.Dto(LiveDashboardTeamDetailDto(teamId, participants)))
    }
    /**
     * Erklärt einen Lauf für beendet und zieht die nächsten nach: aktiv sind danach die Läufe mit
     * der frühesten noch offenen Startzeit — meist einer, bei parallelen Starts mehrere.
     *
     * Damit hält sich das Feld ohne Zutun aktuell: Schiedsrichter sehen den Lauf, den sie gerade
     * vorbereiten oder abnehmen, und geben ihn nach der Ergebniskontrolle selbst frei.
     *
     * Steht die Veranstaltung auf `chainProgressionMode = REGATTABUERO`, ist dieser Weg gesperrt —
     * dort beendet ausschließlich das Büro über den Zeitplan
     * ([de.lambda9.ready2race.backend.app.eventSchedule.boundary.EventScheduleService.finishSlot],
     * das denselben [finishMatchInternal] ungegatet aufruft).
     */
    fun finishMatch(
        eventId: UUID,
        matchId: UUID,
        userId: UUID,
        openResults: OpenResultHandling? = null,
    ): App<LiveDashboardError, ApiResponse.NoData> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        val mode = !EventRepo.getChainProgressionMode(eventId).orDie()
        if (mode == ChainProgressionMode.REGATTABUERO) {
            return@comprehension KIO.fail(LiveDashboardError.FinishReservedForOffice)
        }

        !finishMatchInternal(eventId, matchId, userId, openResults, mode)

        noData
    }

    /**
     * Der eigentliche Beenden-Ablauf, geteilt zwischen dem Schiedsrichter-Dashboard ([finishMatch],
     * oben mode-gated) und dem Regattabüro
     * ([de.lambda9.ready2race.backend.app.eventSchedule.boundary.EventScheduleService.finishSlot],
     * das IMMER beenden darf, unabhängig vom Modus — das Büro kann jederzeit eingreifen).
     *
     * Bewusst hier statt im eventSchedule-Paket: `finishMatch` importiert bereits dessen
     * `ScheduleChainService`/`EventScheduleRepo` (liveDashboard -> eventSchedule); ein Aufruf in die
     * Gegenrichtung wäre ein zweiter Paket-Zyklus, der den ersten nur verdoppelt, nicht auflöst.
     * Kotlin kompiliert beide Pakete ohnehin in einem Modul (kein getrennter Kompilierungsschritt
     * wie bei Java-Multimodul-Grenzen) — der Zyklus ist technisch folgenlos. Die Alternative, den
     * ganzen bereits getesteten `finishMatch`-Ablauf samt Kettenlogik nach eventSchedule zu
     * verschieben, hätte den bestehenden Code nur umständlicher gemacht, ohne den Zyklus wirklich
     * zu vermeiden — deshalb bleibt die Logik hier, `EventScheduleService` ruft sie auf.
     */
    internal fun finishMatchInternal(
        eventId: UUID,
        matchId: UUID,
        userId: UUID,
        openResults: OpenResultHandling?,
        mode: ChainProgressionMode,
    ): App<Nothing, Unit> = KIO.comprehension {
        // Sammelentscheidung für die Boote ohne Ergebnis, bevor der Lauf aus der Ansicht geht.
        if (openResults != null) {
            !LiveDashboardRepo.markOpenTeamsFailed(matchId, openResults.name, userId).orDie()
        }

        !CompetitionMatchRepo.update(matchId) {
            // Beenden nimmt die Aktivierung zurück, lässt den Ist-Start aber stehen: Wann der Lauf
            // losgegangen ist, bleibt auch danach eine Tatsache. Deshalb bewusst NICHT über
            // `CompetitionExecutionService.setMatchActivation` — dessen Deaktivieren löscht
            // `started_at` und pausiert den RaceClocker-Abruf, und beides wäre hier falsch.
            activatedAt = null
            finishedAt = LocalDateTime.now()
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        // Der beendete Lauf ist der Anlass, nicht der Startpunkt: Die Kette sucht im ganzen
        // Zeitplan die vorderste Gruppe, die noch etwas offen hat, und handelt dort. Ein zweiter
        // Anstoß (die Folgerunden-Automatik unten ruft sie erneut) findet dieselbe Front vor und
        // tut deshalb nichts mehr — bis zum 10.08.2026 ist er über die eben gerufene Gruppe hinweg
        // zur übernächsten gelaufen und hat eine Startgruppe zu viel an den Start geholt.
        //
        // Steht die Veranstaltung auf DEAKTIVIERT, beendet der Aufruf nur diesen Lauf. Das ist die
        // sichere Wahl, solange der Zeitplan Lücken hat: Startzeiten stehen erst fest, wenn die
        // Läufe einer Runde gesetzt sind, und die Kette würde sonst den falschen Lauf greifen.
        if (mode != ChainProgressionMode.DEAKTIVIERT) {
            val slotTime = !EventScheduleRepo.getSlotBySetupMatch(matchId).orDie()
            // Bei teilweise gepflegtem Zeitstrahl entscheidet jeder Lauf für sich — ein Lauf ohne
            // Slot nutzt die Legacy-Logik, auch wenn andere Läufe Slots haben.
            if (slotTime != null) {
                // Zeitstrahl-Modus: der Kette entlang der Slots folgen, an wartenden Slots geduldig
                // sein (createNewRound stößt die Kette dann später wieder an).
                !ScheduleChainService.decideAndActivate(eventId, userId)
            } else {
                // Legacy: Events ohne Zeitstrahl behalten das bisherige Verhalten.
                val finishedStart = !LiveDashboardRepo.getMatchStartTime(matchId).orDie()
                val candidates = (!LiveDashboardRepo.getActivationCandidates(eventId).orDie())
                    .filter { candidate ->
                        val start = candidate[COMPETITION_MATCH.START_TIME]
                        finishedStart == null || (start != null && start > finishedStart)
                    }
                !activateNext(candidates, userId)
            }
        }

        // Ist die Runde mit diesem Lauf durch, steht die nächste ohne Zutun. Bewusst NACH der
        // Kette: createNewRound stößt sie selbst noch einmal an, wenn sie an einem wartenden Slot
        // geparkt war.
        !AutoRoundProgressionService.progressAfterMatch(eventId, matchId, userId)

        // Der gemeinsame Trichter fürs Beenden (Dashboard UND Büro über finishSlot) — ein Bump
        // deckt den Stempel, die Ketten-Aktivierung und die Folgerunden-Automatik zusammen ab.
        EventChangeMarker.bump(eventId)

        KIO.unit
    }

    /**
     * Ruft einen Lauf an den Start oder nimmt das zurück — manuelles Übersteuern, falls zu viele
     * oder zu wenige Läufe am Start stehen.
     *
     * Was dabei geschrieben wird (und warum Deaktivieren die RaceClocker-Automatik pausiert), steht
     * an der geteilten Stelle: [CompetitionExecutionService.setMatchActivation]. Das
     * Durchführungs-Tab kommt über denselben Weg — sonst hinge dieselbe Regel an zwei Orten.
     */
    fun setMatchActivated(
        eventId: UUID,
        matchId: UUID,
        activated: Boolean,
        userId: UUID,
    ): App<LiveDashboardError, ApiResponse.NoData> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        !CompetitionExecutionService.setMatchActivation(matchId, activated, userId)

        // setMatchActivation selbst kennt die Veranstaltung nicht — beide Aufrufer (hier und
        // CompetitionExecutionService.updateMatchActivation) bumpen deshalb selbst.
        EventChangeMarker.bump(eventId)

        noData
    }

    /**
     * Markiert den echten Start eines Laufs — getrennt von der geplanten Startzeit. Idempotent:
     * ein zweiter Aufruf verschiebt den Zeitstempel nicht mehr, er ist nur beim ersten Mal gesetzt.
     * Zugleich geht der Lauf auf "aktiv", da "gestartet" ohne "am Start gerufen" keinen Sinn ergibt.
     *
     * Der Knopf heißt in der Oberfläche „Läuft" und nicht „Start": Er stellt fest, dass das Rennen
     * unterwegs ist, er löst keine Zeitnahme aus. Wo RaceClocker abgerufen wird, meldet der Feed
     * den Start ohnehin selbst — der Knopf bleibt für den Ausfall und für Zeitnahmen ohne
     * Startstempel.
     */
    fun markMatchStarted(
        eventId: UUID,
        matchId: UUID,
        userId: UUID,
    ): App<LiveDashboardError, ApiResponse.NoData> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        !CompetitionMatchRepo.update(matchId) {
            if (startedAt == null) {
                startedAt = LocalDateTime.now()
            }
            if (activatedAt == null) {
                activatedAt = LocalDateTime.now()
            }
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        // „Läuft" soll sofort auf den Anzeigen stehen, nicht erst nach Ablauf der Cache-TTL.
        EventChangeMarker.bump(eventId)

        noData
    }

    /**
     * Hängt eine Notiz an ein Boot in einem Lauf - direkter Austausch zwischen Schiedsrichtern
     * über das Dashboard ("Boje berührt"), keine Wertung. [teamId] ist wie überall im Dashboard
     * die Meldungs-Kennung; die Boots-Zeile dazu löst [MatchTeamNoteRepo.findTeamRowId] auf.
     *
     * Append-only: Es gibt kein Ändern, eine Korrektur ist Löschen + neu anlegen. So braucht es
     * kein Sperren - schreiben zwei Schiedsrichter gleichzeitig, entstehen zwei Einträge.
     */
    fun createTeamNote(
        eventId: UUID,
        matchId: UUID,
        teamId: UUID,
        userId: UUID,
        request: MatchTeamNoteRequest,
    ): App<LiveDashboardError, ApiResponse.Created> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        val teamRowId = !MatchTeamNoteRepo.findTeamRowId(eventId, matchId, teamId).orDie()
            ?: return@comprehension KIO.fail(LiveDashboardError.TeamNotFound(teamId))

        val noteId = UUID.randomUUID()
        !MatchTeamNoteRepo.create(
            MatchTeamNoteRecord(
                id = noteId,
                competitionMatchTeam = teamRowId,
                // Getrimmt gespeichert, damit der DB-Check (btrim <> '') und die Anzeige dasselbe
                // sehen - der Validator hat Leerraum-only bereits abgewiesen.
                note = request.note.trim(),
                createdAt = LocalDateTime.now(),
                createdBy = userId,
            )
        ).orDie()

        KIO.ok(ApiResponse.Created(noteId))
    }

    /**
     * Löscht eine Notiz - erlaubt für alle mit demselben Schreibrecht, nicht nur für die Autorin:
     * Die Notizen sind ein Werkzeug für den internen Austausch, und eine falsche Notiz muss auch
     * dann entfernbar sein, wenn die Autorin gerade auf dem Wasser ist.
     */
    fun deleteTeamNote(
        eventId: UUID,
        matchId: UUID,
        teamId: UUID,
        noteId: UUID,
    ): App<LiveDashboardError, ApiResponse.NoData> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        // Erst die Zuordnung prüfen, dann löschen: eine erratene Kennung darf nicht quer über
        // Läufe oder Veranstaltungen löschen. Der Team-Check läuft über denselben Pfad wie das
        // Anlegen und deckt damit auch die Veranstaltung ab.
        val teamRowId = !MatchTeamNoteRepo.findTeamRowId(eventId, matchId, teamId).orDie()
        if (teamRowId == null) {
            return@comprehension KIO.fail(LiveDashboardError.TeamNotFound(teamId))
        }

        val belongsToTeam = !MatchTeamNoteRepo.existsForTeam(noteId, matchId, teamId).orDie()
        if (!belongsToTeam) {
            return@comprehension KIO.fail(LiveDashboardError.NoteNotFound(noteId))
        }

        !MatchTeamNoteRepo.delete(noteId).orDie()

        noData
    }

    /**
     * Was die Verwaltung braucht: die Wettkämpfe, die einstellbaren Prüfungen samt ihren
     * Standardwerten und die bisherigen Abweichungen. Die Zeitfenster-Prüfung erscheint nur für
     * Bedingungen, für die überhaupt ein Fenster konfiguriert ist - sonst gäbe es nichts zu
     * bewerten.
     */
    fun getCheckSeverityConfig(
        eventId: UUID,
    ): App<LiveDashboardError, ApiResponse.Dto<CheckSeverityConfigDto>> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        val competitionRecords = !CheckSeverityRepo.getCompetitions(eventId).orDie()
        val requirementRecords = !LiveDashboardRepo.getEventRequirements(eventId).orDie()
        val entryRecords = !CheckSeverityRepo.getByEvent(eventId).orDie()

        // Dieselbe Bedingung kann mehreren Rollen zugeordnet sein und taucht dann mehrfach auf -
        // eingestellt wird sie trotzdem nur einmal.
        val requirements = requirementRecords.distinctBy { it[PARTICIPANT_REQUIREMENT.ID] }

        val rows = buildList {
            add(CheckSeverityRowDto(CheckType.INVOICE_OPEN, null, null))
            add(CheckSeverityRowDto(CheckType.NOT_IN_ARENA, null, null))
            requirements.forEach { req ->
                val id = req[PARTICIPANT_REQUIREMENT.ID]!!
                add(CheckSeverityRowDto(CheckType.REQUIREMENT, id, req[PARTICIPANT_REQUIREMENT.NAME]))
                val hasWindow = req[PARTICIPANT_REQUIREMENT.CHECK_EARLIEST_MINUTES_BEFORE] != null ||
                    req[PARTICIPANT_REQUIREMENT.CHECK_LATEST_MINUTES_BEFORE] != null
                if (hasWindow) {
                    add(
                        CheckSeverityRowDto(
                            CheckType.REQUIREMENT_TIME_WINDOW,
                            id,
                            req[PARTICIPANT_REQUIREMENT.NAME],
                        )
                    )
                }
            }
        }

        val optionalById = requirements.associate {
            it[PARTICIPANT_REQUIREMENT.ID]!! to (it[PARTICIPANT_REQUIREMENT.OPTIONAL] == true)
        }

        KIO.ok(
            ApiResponse.Dto(
                CheckSeverityConfigDto(
                    competitions = competitionRecords.map {
                        CheckSeverityCompetitionDto(
                            competitionId = it[COMPETITION.ID]!!,
                            identifier = it[COMPETITION_PROPERTIES.IDENTIFIER]!!,
                            name = it[COMPETITION_PROPERTIES.NAME]!!,
                            checkInOutRequired = it[COMPETITION_PROPERTIES.CHECK_IN_OUT_REQUIRED] == true,
                        )
                    },
                    rows = rows,
                    defaults = rows.map { row ->
                        CheckSeverityRowDefaultDto(
                            checkType = row.checkType,
                            requirementId = row.requirementId,
                            severity = LiveDashboardLogic.defaultSeverity(
                                row.checkType,
                                row.requirementId?.let { optionalById[it] } == true,
                            ),
                        )
                    },
                    entries = entryRecords.mapNotNull { r ->
                        val type = CheckType.entries
                            .firstOrNull { it.name == r[COMPETITION_CHECK_SEVERITY.CHECK_TYPE] }
                            ?: return@mapNotNull null
                        val severity = CheckSeverity.entries
                            .firstOrNull { it.name == r[COMPETITION_CHECK_SEVERITY.SEVERITY] }
                            ?: return@mapNotNull null
                        CheckSeverityEntryDto(
                            competitionId = r[COMPETITION_CHECK_SEVERITY.COMPETITION]!!,
                            checkType = type,
                            requirementId = r[COMPETITION_CHECK_SEVERITY.PARTICIPANT_REQUIREMENT],
                            severity = severity,
                        )
                    },
                )
            )
        )
    }

    /**
     * Ersetzt die Abweichungen der Veranstaltung komplett (siehe [CheckSeverityRepo.replaceForEvent]) -
     * ein Eintrag, der hier nicht ankommt, ist damit gelöscht. Einträge, die dem Standard
     * entsprechen, werden verworfen statt gespeichert: die Tabelle bleibt dünn, und ein später
     * geänderter Standard wirkt auch auf Bestandsdaten. Die eigentliche Auswahl steckt in
     * [LiveDashboardLogic.entriesToPersist] - dort auch, warum ein Eintrag einer vorübergehend
     * abgemeldeten, aber bereits gespeicherten Bedingung trotzdem erhalten bleiben muss: der
     * Verwaltungsdialog schickt ihn genau deshalb unverändert mit, weil dieser Schreibweg ihn
     * sonst löschen würde.
     */
    fun updateCheckSeverityConfig(
        eventId: UUID,
        request: UpdateCheckSeverityRequest,
        userId: UUID,
    ): App<LiveDashboardError, ApiResponse.NoData> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        val optionalById = !LiveDashboardRepo.getEventRequirements(eventId).orDie().map { rows ->
            rows.associate {
                it[PARTICIPANT_REQUIREMENT.ID]!! to (it[PARTICIPANT_REQUIREMENT.OPTIONAL] == true)
            }
        }
        val competitionIds = !CheckSeverityRepo.getCompetitions(eventId).orDie()
            .map { rows -> rows.mapNotNull { it[COMPETITION.ID] }.toSet() }
        // Bereits gespeicherte Bedingungs-Kennungen, konsistent zu getCheckSeverityConfig oben
        // gebildet: eine davon darf eine Zeile tragen, auch wenn sie gerade nicht in optionalById
        // steckt - siehe die Begründung an entriesToPersist.
        val persistedRequirementIds = !CheckSeverityRepo.getByEvent(eventId).orDie()
            .map { rows -> rows.mapNotNull { it[COMPETITION_CHECK_SEVERITY.PARTICIPANT_REQUIREMENT] }.toSet() }

        val now = LocalDateTime.now()
        val records = LiveDashboardLogic.entriesToPersist(
            entries = request.entries,
            competitionIds = competitionIds,
            optionalByRequirement = optionalById,
            persistedRequirementIds = persistedRequirementIds,
        )
            .map {
                CompetitionCheckSeverityRecord(
                    competition = it.competitionId,
                    checkType = it.checkType.name,
                    participantRequirement = it.requirementId,
                    severity = it.severity.name,
                    createdAt = now,
                    createdBy = userId,
                    updatedAt = now,
                    updatedBy = userId,
                )
            }

        !CheckSeverityRepo.replaceForEvent(eventId, records).orDie()

        noData
    }

    /**
     * Die veranstaltungsweiten Daten, die für jede Mannschaft gleich sind — einmal aufbereitet
     * statt je Mannschaft neu gruppiert.
     */
    private class ParticipantContext(
        requirementRecords: List<Record>,
        checkRecords: List<Record>,
        substitutionRecords: List<SubstitutionViewRecord>,
        /** Abweichende Schweregrade der Veranstaltung, siehe [CheckSeverityConfig]. */
        val severityConfig: CheckSeverityConfig,
        /** Der getragene Verein je Person, siehe [wornClubsByParticipant]. */
        val wornClubs: Map<UUID, String>,
        /**
         * Letzter Steg-Scan je Person (Typ und Zeitpunkt). Leer, wo die Ansicht die Scans nicht
         * erhebt - die Übersichtskarten fassen sie schon je Boot zusammen und brauchen sie hier
         * nicht noch einmal je Person.
         */
        val lastScans: Map<UUID, Pair<String, LocalDateTime>> = emptyMap(),
    ) {
        /** requirement id -> assigned named participants (null element = global assignment) */
        val requirementAssignments = requirementRecords.groupBy(
            { it[PARTICIPANT_REQUIREMENT.ID]!! },
            { it[EVENT_HAS_PARTICIPANT_REQUIREMENT.NAMED_PARTICIPANT] },
        )

        val requirementInfos = requirementRecords.distinctBy { it[PARTICIPANT_REQUIREMENT.ID] }

        val checksByKey = checkRecords.associateBy {
            it[PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT]!! to
                it[PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT_REQUIREMENT]!!
        }

        /** (round, registration) -> substitutions applying to that team in that round, ordered */
        val substitutionsByKey = substitutionRecords
            .groupBy { it.competitionSetupRoundId!! to it.competitionRegistrationId!! }
            .mapValues { (_, subs) -> subs.sortedBy { it.orderForRound } }
    }

    /**
     * Der Verein, den jede Person *trägt*, je Personen-Kennung - die Regel selbst steht in
     * [ClubComposition.clubWorn], damit Board, Athleten-Anzeige und Urkunde nicht drei Fassungen
     * davon pflegen.
     *
     * Zwei Quellen, weil eine nicht reicht: [teamRecords] deckt alle gemeldeten Personen ab, eine
     * Ummeldung darf aber ein Vereinsmitglied hereinholen, das für diese Veranstaltung nirgends
     * gemeldet ist. Für genau die wird nachgeschlagen - meist ist die Menge leer und die zweite
     * Abfrage entfällt.
     */
    private fun wornClubsByParticipant(
        teamRecords: List<Record>,
        substitutionRecords: List<SubstitutionViewRecord>,
    ): App<Nothing, Map<UUID, String>> = KIO.comprehension {
        val fromRegistrations = teamRecords.mapNotNull { row ->
            val id = row.get("participant_id", UUID::class.java) ?: return@mapNotNull null
            ClubComposition.clubWorn(
                external = row[PARTICIPANT.EXTERNAL],
                externalClubName = row[PARTICIPANT.EXTERNAL_CLUB_NAME],
                ownClubName = row.get("participant_club_name", String::class.java),
            )?.let { id to it }
        }.toMap()

        val missing = substitutionRecords.mapNotNull { it.participantIn?.id }.toSet() - fromRegistrations.keys
        if (missing.isEmpty()) {
            return@comprehension KIO.ok(fromRegistrations)
        }

        val fromSubstitutions = !LiveDashboardRepo.getParticipantClubs(missing).orDie().map { rows ->
            rows.mapNotNull { row ->
                val id = row[PARTICIPANT.ID] ?: return@mapNotNull null
                ClubComposition.clubWorn(
                    external = row[PARTICIPANT.EXTERNAL],
                    externalClubName = row[PARTICIPANT.EXTERNAL_CLUB_NAME],
                    ownClubName = row.get("participant_club_name", String::class.java),
                )?.let { id to it }
            }.toMap()
        }

        KIO.ok(fromRegistrations + fromSubstitutions)
    }

    /**
     * Löst die gemeldete Aufstellung einer Mannschaft gegen die Ummeldungen der Runde auf und
     * hängt jeder Person ihre Teilnahmebedingungen an.
     */
    private fun buildParticipants(
        rows: List<Record>,
        registrationId: UUID,
        startTime: LocalDateTime?,
        context: ParticipantContext,
        competitionId: UUID,
    ): App<Nothing, List<LiveDashboardParticipantDto>> = KIO.comprehension {
        val first = rows.first()
        val clubId = first.get("club_id", UUID::class.java)
        val clubName = first.get("club_name", String::class.java)
        val teamName = first.get("team_name", String::class.java)
        val roundId = first.get("round_id", UUID::class.java)!!

        val registered = rows.mapNotNull { row ->
            val participantId = row.get("participant_id", UUID::class.java)
            val namedParticipantId = row.get("named_participant_id", UUID::class.java)
            if (participantId == null || namedParticipantId == null) {
                null
            } else {
                ParticipantForExecutionDto(
                    id = participantId,
                    namedParticipantId = namedParticipantId,
                    namedParticipantName = row.get("named_role", String::class.java) ?: "",
                    firstName = row[PARTICIPANT.FIRSTNAME] ?: "",
                    lastName = row[PARTICIPANT.LASTNAME] ?: "",
                    year = row[PARTICIPANT.YEAR]!!,
                    gender = row[PARTICIPANT.GENDER]!!,
                    clubId = clubId!!,
                    clubName = clubName ?: "",
                    competitionRegistrationId = registrationId,
                    competitionRegistrationName = teamName,
                    external = row[PARTICIPANT.EXTERNAL],
                    externalClubName = row[PARTICIPANT.EXTERNAL_CLUB_NAME],
                )
            }
        }.distinctBy { it.id to it.namedParticipantId }

        val subs = context.substitutionsByKey[roundId to registrationId] ?: emptyList()

        // Post-substitution crew: this is the crew that actually starts, incl. taken-over roles.
        val resolved = !CompetitionExecutionService.getActuallyParticipatingParticipants(registered, subs)

        // Who was substituted in for whom, so the dashboard can show the change
        // instead of silently presenting a different crew than the one that was entered.
        val substitutedForByParticipant = subs
            .filter { it.participantIn != null }
            .associateBy({ it.participantIn!!.id!! }, { it })

        KIO.ok(
            resolved.map { p ->
                val substitution = substitutedForByParticipant[p.id]
                val replaced = substitution?.participantOut

                val requirements = context.requirementInfos
                    .filter { req ->
                        LiveDashboardLogic.requirementApplies(
                            context.requirementAssignments[req[PARTICIPANT_REQUIREMENT.ID]!!] ?: emptyList(),
                            p.namedParticipantId,
                        )
                    }
                    .map { req ->
                        val requirementId = req[PARTICIPANT_REQUIREMENT.ID]!!
                        val check = context.checksByKey[p.id to requirementId]
                        // checked und timeCheck aus derselben Quelle (check) ableiten: requirementSeverity
                        // verlässt sich darauf, dass ein nicht abgehaktes Ergebnis nie LATE/TOO_EARLY
                        // trägt - computeTimeCheck liefert dafür ohne checkedAt immer NOT_CHECKED.
                        val checked = check != null
                        val optional = req[PARTICIPANT_REQUIREMENT.OPTIONAL]!!
                        val timeCheck = LiveDashboardLogic.computeTimeCheck(
                            startTime = startTime,
                            checkedAt = check?.get(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.CREATED_AT),
                            earliestMinutesBefore = req[PARTICIPANT_REQUIREMENT.CHECK_EARLIEST_MINUTES_BEFORE],
                            latestMinutesBefore = req[PARTICIPANT_REQUIREMENT.CHECK_LATEST_MINUTES_BEFORE],
                        )
                        LiveDashboardRequirementStatusDto(
                            requirementId = requirementId,
                            name = req[PARTICIPANT_REQUIREMENT.NAME]!!,
                            description = req[PARTICIPANT_REQUIREMENT.DESCRIPTION],
                            optional = optional,
                            checked = checked,
                            checkedAt = check?.get(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.CREATED_AT),
                            note = check?.get(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.NOTE),
                            timeCheck = timeCheck,
                            severity = LiveDashboardLogic.requirementSeverity(
                                checked = checked,
                                timeCheckStatus = timeCheck?.status,
                                missingSeverity = context.severityConfig.severityFor(
                                    competitionId, CheckType.REQUIREMENT, requirementId, optional
                                ),
                                timeWindowSeverity = context.severityConfig.severityFor(
                                    competitionId, CheckType.REQUIREMENT_TIME_WINDOW, requirementId
                                ),
                            ),
                        )
                    }

                LiveDashboardParticipantDto(
                    participantId = p.id,
                    firstName = p.firstName,
                    lastName = p.lastName,
                    namedRole = p.namedParticipantName,
                    year = p.year,
                    gender = p.gender.name,
                    // p.clubName wäre der meldende Verein - für alle gleich, und damit genau die
                    // Angabe, die mehrere Boote eines Laufs ununterscheidbar gemacht hat.
                    clubName = context.wornClubs[p.id],
                    substitutedFor = replaced?.let { "${it.firstname} ${it.lastname}" },
                    substitutionReason = substitution?.reason,
                    requirements = requirements,
                    trackingStatus = context.lastScans[p.id]?.first,
                    trackingAt = context.lastScans[p.id]?.second,
                )
            }
        )
    }

    private fun activateNext(candidates: List<Record>, userId: UUID): App<Nothing, Unit> =
        KIO.comprehension {
            val nextStart = candidates.firstOrNull()?.get(COMPETITION_MATCH.START_TIME)
                ?: return@comprehension KIO.unit

            // Alle Läufe derselben Startzeit gemeinsam aktivieren: parallele Starts gehören zusammen.
            !candidates
                .filter { it[COMPETITION_MATCH.START_TIME] == nextStart }
                .traverse {
                    CompetitionExecutionService.setMatchActivation(
                        it[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!, activated = true, userId = userId,
                    )
                }

            KIO.unit
        }

}
