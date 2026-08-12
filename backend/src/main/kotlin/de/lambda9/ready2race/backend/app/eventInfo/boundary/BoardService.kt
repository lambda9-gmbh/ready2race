package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyService
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.BoardRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.MyEventRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.toAthleteBoardMatch
import de.lambda9.ready2race.backend.app.participantRequirement.control.ParticipantRequirementForEventRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.toAthleteBoardResult
import de.lambda9.ready2race.backend.app.eventInfo.control.toDto
import de.lambda9.ready2race.backend.app.eventInfo.control.toJsonb
import de.lambda9.ready2race.backend.app.eventInfo.control.toNameDto
import de.lambda9.ready2race.backend.app.eventInfo.control.toRecord
import de.lambda9.ready2race.backend.app.eventInfo.entity.*
import de.lambda9.ready2race.backend.app.eventSchedule.control.EventScheduleRepo
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_SCHEDULE_SLOT
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.recover
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object BoardService {

    // Zwischenspeicher je Board, aus demselben Grund wie beim alten Athleten-Board:
    // der Resolve-Endpoint ist öffentlich und wird von jedem montierten Bildschirm und
    // jedem Telefon im Takt abgerufen — die Datenbank zahlt höchstens einmal je
    // CACHE_TTL_SECONDS und Board. serverTime bleibt je Antwort frisch (Countdown-Anker).
    // [marker] ist der [EventChangeMarker]-Stand beim Bau: schreibt danach jemand an der
    // Veranstaltung (Ergebnis, Beenden, Hinweis, …), gilt der Eintrag sofort als alt —
    // die TTL deckelt nur noch den Nichts-passiert-Fall.
    private data class CachedView(val builtAt: LocalDateTime, val marker: Long, val dto: BoardViewDto)

    private val boardViewCache = ConcurrentHashMap<UUID, CachedView>()

    fun getBoards(eventId: UUID): App<EventInfoProblem, ApiResponse.ListDto<BoardDto>> =
        KIO.comprehension {
            val exists = !EventRepo.exists(eventId).orDie()
            if (!exists) {
                !KIO.fail<EventInfoProblem>(EventInfoProblem.EventNotFound(eventId))
            }
            val boards = !BoardRepo.findByEvent(eventId).orDie()
            KIO.ok(ApiResponse.ListDto(boards.map { it.toDto() }))
        }

    /** Öffentliche Kurzliste: trägt die Umleitung der alten Athleten-Board-URL. */
    fun getBoardNames(eventId: UUID): App<EventInfoProblem, ApiResponse.ListDto<BoardNameDto>> =
        KIO.comprehension {
            val exists = !EventRepo.exists(eventId).orDie()
            if (!exists) {
                !KIO.fail<EventInfoProblem>(EventInfoProblem.EventNotFound(eventId))
            }
            val boards = !BoardRepo.findByEvent(eventId).orDie()
            KIO.ok(ApiResponse.ListDto(boards.map { it.toNameDto() }))
        }

    fun createBoard(eventId: UUID, request: BoardRequest): App<EventInfoProblem, ApiResponse.Dto<BoardDto>> =
        KIO.comprehension {
            val exists = !EventRepo.exists(eventId).orDie()
            if (!exists) {
                !KIO.fail<EventInfoProblem>(EventInfoProblem.EventNotFound(eventId))
            }
            val record = request.toRecord(eventId)
            !BoardRepo.create(record).orDie()
            KIO.ok(ApiResponse.Dto(record.toDto()))
        }

    fun updateBoard(boardId: UUID, request: BoardRequest): App<EventInfoProblem, ApiResponse.NoData> =
        KIO.comprehension {
            val updated = !BoardRepo.update(boardId) {
                name = request.name
                config = request.config.toJsonb()
                updatedAt = LocalDateTime.now()
            }.orDie()

            if (updated == null) {
                KIO.fail(EventInfoProblem.BoardNotFound(boardId))
            } else {
                // Ein montierter Bildschirm soll die neue Konfiguration mit dem nächsten
                // Poll sehen, nicht erst nach Ablauf des Zwischenspeichers.
                boardViewCache.remove(boardId)
                KIO.ok(ApiResponse.NoData)
            }
        }

    fun deleteBoard(boardId: UUID): App<EventInfoProblem, ApiResponse.NoData> =
        KIO.comprehension {
            val deleted = !BoardRepo.delete(boardId).orDie()
            if (deleted < 1) {
                !KIO.fail<EventInfoProblem>(EventInfoProblem.BoardNotFound(boardId))
            }
            boardViewCache.remove(boardId)
            noData
        }

    /**
     * Alles, was die Anzeige eines Boards braucht, in einer Antwort: Konfiguration plus
     * die aufgelösten Timeline-Slots und Listen. Abgerufen wird nur, was die Konfiguration
     * wirklich braucht ([BoardLogic.dataNeeds]).
     */
    fun getBoardView(eventId: UUID, boardId: UUID): App<EventInfoProblem, ApiResponse.Dto<BoardViewDto>> =
        KIO.comprehension {
            val now = LocalDateTime.now()

            // Markerstand VOR dem Bau lesen: fällt ein Schreibzugriff mitten in den Bau, trägt
            // der Eintrag den alten Stand und ist beim nächsten Abruf sofort wieder alt —
            // lieber einmal umsonst neu bauen als einen veralteten Stand für die TTL halten.
            val marker = EventChangeMarker.current(eventId)

            val cached = boardViewCache[boardId]
                ?.takeIf { BoardLogic.isCacheFresh(it.builtAt, now) && it.marker == marker }
            if (cached != null) {
                KIO.ok(ApiResponse.Dto(cached.dto.copy(serverTime = now)))
            } else {
                val record = !BoardRepo.findById(boardId).orDie()
                if (record == null || record.eventId != eventId) {
                    !KIO.fail<EventInfoProblem>(EventInfoProblem.BoardNotFound(boardId))
                }
                val eventName = !EventRepo.getName(eventId).orDie()
                if (eventName == null) {
                    !KIO.fail<EventInfoProblem>(EventInfoProblem.EventNotFound(eventId))
                }

                val board = record!!.toDto()
                val needs = BoardLogic.dataNeeds(board.config)

                // Einmal je Aufbau, nicht je Mannschaft — die drei Blöcke lösen zusammen
                // leicht hundert Vereinsnamen auf.
                val clubShortNames = !EventInfoService.clubShortNames()

                // startState immer mit Countdown berechnen; ob er erscheint, entscheidet
                // das Element auf dem Gerät (AthleteBoardMatchCard).
                // getRunningMatches liefert aufsteigend nach Startzeit (getRunningMatches
                // in CompetitionMatchRepo sortiert nach started_at), getMatchResults
                // neuestes zuerst — die Reihenfolgen, auf denen BoardLogic.resolveOffset
                // aufbaut. Sprecherinnen-Details (Crew einzeln, Jahrgänge, meldender
                // Verein) kommen nur mit, wenn irgendein Element sie anfordert.
                val details = needs.crewDetails
                val runningInfos =
                    (!EventInfoService.getRunningMatches(eventId, needs.runningLimit, clubShortNames)).data
                val upcomingInfos =
                    (!EventInfoService.getUpcomingMatchesForBoard(eventId, needs.upcomingLimit, clubShortNames)).data
                // confirmedOnly: die „Letztes Ergebnis"-Kachel zeigt nur beendete Läufe —
                // ein voll gewerteter, unbeendeter läuft nebenan noch als „Im Rennen" mit
                // Live-Stand und würde sonst doppelt erscheinen (Begründung am Parameter).
                val resultInfos = (!EventInfoService.getLatestMatchResults(
                    eventId,
                    needs.resultsLimit,
                    null,
                    clubShortNames,
                    confirmedOnly = true,
                )).data

                // Bedingungen je Person nur, wenn eine Sprecher-Kachel sie anfordert
                // (needs.requirements) — drei Extra-Abfragen und je Person Nutzlast, die kein
                // anderes Board zahlt. Vor der Konversion gesammelt, weil nur die Info-Typen
                // die Personen-Kennungen tragen; welche Slots die Kachel am Ende zeigt, steht
                // hier noch nicht fest, deshalb alle drei Blöcke.
                val requirements = if (needs.requirements) {
                    val participantIds = (
                        runningInfos.flatMap { m -> m.teams.flatMap { it.participants } }.map { it.participantId } +
                            upcomingInfos.flatMap { m -> m.teams.flatMap { it.participants } }.map { it.participantId } +
                            resultInfos.flatMap { m -> m.teams.flatMap { it.participants } }.map { it.participantId }
                        ).toSet()
                    !participantRequirements(eventId, participantIds)
                } else emptyMap()

                var running = runningInfos.map {
                    it.toAthleteBoardMatch(now, showCountdown = true, includeDetails = details, requirements = requirements)
                }
                var upcoming = AthleteBoardLogic.sortByStartTime(
                    upcomingInfos.map {
                        it.toAthleteBoardMatch(now, showCountdown = true, includeDetails = details, requirements = requirements)
                    }
                ) { it.startTime }
                var results = resultInfos.map {
                    it.toAthleteBoardResult(includeDetails = details, requirements = requirements)
                }

                // „Weiter kommen N Boote → Finale": matchId IST die Setup-Lauf-Id; die
                // Platzhalter der Zeitstrahl-Slots treffen in der Abfrage schlicht nichts.
                if (needs.advancement) {
                    val advancement = !BoardRepo
                        .advancementBySetupMatch((running + upcoming).map { it.matchId }.toSet())
                        .orDie()
                    fun AthleteBoardMatch.withAdvancement() = advancement[matchId]
                        ?.let { copy(nextRoundName = it.nextRoundName, advancingSeats = it.seats) }
                        ?: this
                    running = running.map { it.withAdvancement() }
                    upcoming = upcoming.map { it.withAdvancement() }
                }

                // Gemessene Boot-Starts nur für die Sprecher-Kachel (needs-Muster wie die
                // Bedingungen): beim Zeitfahren startet jedes Boot einzeln
                // (competition_match_team.started_at aus dem RaceClocker-Feed), und die
                // Sprecherin will wissen, wer schon unterwegs ist. Anstehende Läufe haben
                // keine Starts; zugeordnet wird über (Lauf, Startnummer), weil die
                // Anzeige-DTOs keine Meldungs-IDs führen. Alle anderen Boards zahlen weder
                // die Abfrage noch Nutzlast (NON_NULL-Serialisierung lässt null-Felder weg).
                if (needs.boatStarts) {
                    val boatStarts = !BoardRepo
                        .boatStarts((running.map { it.matchId } + results.map { it.matchId }).toSet())
                        .orDie()
                    if (boatStarts.isNotEmpty()) {
                        running = running.map { m ->
                            m.copy(teams = m.teams.map { t ->
                                t.copy(startedAt = boatStarts[m.matchId to t.startNumber])
                            })
                        }
                        results = results.map { m ->
                            m.copy(teams = m.teams.map { t ->
                                t.copy(startedAt = boatStarts[m.matchId to t.startNumber])
                            })
                        }
                    }
                }

                // Ehrungen sind bewusst fehlertolerant: eine veraltete Kachel-Konfiguration
                // darf das Board nicht mitreißen (siehe boardCeremonies).
                val ceremonies = !AwardCeremonyService.boardCeremonies(eventId, needs.ceremonies)
                    .recover { KIO.ok(emptyList()) }

                val program = if (needs.schedule) !buildProgram(eventId) else emptyList()

                // Der Hinweis liegt mit im View-Zwischenspeicher; sein PUT bumpt den
                // EventChangeMarker, eine Änderung erscheint also mit dem nächsten
                // Poll-Takt des Bildschirms.
                val notice = !EventRepo.getNotice(eventId).orDie()

                // Nur für Boards mit DELAY-Element (needs-Muster): der Running-Block trägt die
                // Zahl nicht verlässlich — der zuletzt gestartete Lauf kann längst beendet und
                // dort verschwunden sein. Deshalb die eigene kleine Abfrage, gated statt gratis.
                val currentDelaySeconds = if (needs.delay) {
                    BoardLogic.currentDelaySeconds(!CompetitionMatchRepo.getLatestStartedTimes(eventId).orDie())
                } else null

                val dto = BoardViewDto(
                    boardId = board.id,
                    eventName = eventName!!,
                    serverTime = now,
                    refreshIntervalSeconds = board.config.refreshIntervalSeconds
                        .coerceAtLeast(BoardLimits.MIN_REFRESH_INTERVAL_SECONDS),
                    config = board.config,
                    slots = needs.offsets.sorted().map { BoardLogic.resolveOffset(it, running, upcoming, results) },
                    lists = needs.listLimits.map { (mode, limit) ->
                        when (mode) {
                            BoardListMode.RUNNING -> BoardListDto(mode, running.take(limit), emptyList())
                            BoardListMode.UPCOMING -> BoardListDto(mode, upcoming.take(limit), emptyList())
                            BoardListMode.RESULTS -> BoardListDto(mode, emptyList(), results.take(limit))
                            // Das Tagesprogramm kommt ungekürzt: die Anzeige zentriert
                            // selbst um „jetzt" und schneidet dort zu (boardView.ts).
                            BoardListMode.SCHEDULE -> BoardListDto(mode, emptyList(), emptyList(), program)
                        }
                    },
                    ceremonies = ceremonies,
notice = notice,
                    currentDelaySeconds = currentDelaySeconds,
                )

                boardViewCache[boardId] = CachedView(now, marker, dto)
                KIO.ok(ApiResponse.Dto(dto))
            }
        }

    /**
     * Das Tagesprogramm aus dem Zeitplan: jeder Slot mit seinem Zustand. Entfallene
     * Slots bleiben draußen; Pausen (FREE-Slots) nur, wenn die Veranstaltung sie auf
     * öffentlichen Anzeigen zeigt — dieselbe Regel wie bei den Platzhaltern der
     * Lauf-Blöcke (EventInfoService.mergeWithPendingPlaceholders).
     */
    /**
     * Bedingungen je Person für die Sprecher-Kachel — mit der DATENSCHUTZ-Leitplanke des
     * persönlichen Dashboards (MyEventService): Boards sind öffentliche Endpunkte, deshalb
     * verlassen ausschließlich Bedingungen den Server, die (a) für die Rolle der Person
     * überhaupt gelten (rollengebundene Bedingungen zählen nur für Personen in dieser Rolle,
     * Regel aus `ParticipantRequirementForEventRepo.getRequirementsForNamedParticipants`) und
     * (b) ausdrücklich `publicly_visible = true` tragen. Freitext-Notizen werden gar nicht
     * erst geladen (`MyEventRepo.findFulfilledRequirementIdsByParticipant`). Gebatcht auf drei
     * Abfragen für alle Personen zusammen — die Boards fragen im Sekundentakt ab.
     */
    private fun participantRequirements(
        eventId: UUID,
        participantIds: Set<UUID>,
    ): App<Nothing, Map<UUID, List<AthleteBoardRequirement>>> = KIO.comprehension {
        if (participantIds.isEmpty()) return@comprehension KIO.ok(emptyMap())

        val visible = (!ParticipantRequirementForEventRepo.get(eventId, onlyActive = true).orDie())
            .filter { it.publiclyVisible == true }
        if (visible.isEmpty()) return@comprehension KIO.ok(emptyMap())

        val rolesByParticipant =
            !MyEventRepo.findNamedParticipantIdsByParticipant(eventId, participantIds).orDie()
        val bindings = !ParticipantRequirementForEventRepo
            .getRequirementsForNamedParticipants(
                eventId,
                rolesByParticipant.values.flatten().distinct(),
            )
            .orDie()
        val fulfilledByParticipant =
            !MyEventRepo.findFulfilledRequirementIdsByParticipant(eventId, participantIds).orDie()

        KIO.ok(participantIds.associateWith { participantId ->
            val roles = rolesByParticipant[participantId] ?: emptySet()
            val applicable = bindings
                .filter { it.namedParticipant == null || roles.contains(it.namedParticipant) }
                .map { it.participantRequirement }
                .toSet()
            visible
                .filter { applicable.contains(it.id) }
                .map {
                    AthleteBoardRequirement(
                        name = it.name ?: "",
                        fulfilled = fulfilledByParticipant[participantId]?.contains(it.id) == true,
                    )
                }
                .sortedBy { it.name }
        })
    }

    private fun buildProgram(eventId: UUID): App<EventInfoProblem, List<BoardProgramEntry>> =
        KIO.comprehension {
            val showBreaks = !EventRepo.getShowBreaksOnPublicBoards(eventId).orDie()
            val slotRecords = !EventScheduleRepo.getSlots(eventId).orDie()

            KIO.ok(
                // markPassedFreeSlots: Programmpunkte ohne eigenen Erledigt-Zustand gelten als
                // vorbei, sobald ein späterer Lauf Aktivität zeigt — sonst bleibt der
                // mitlaufende Ausschnitt an einer überholten Pause hängen.
                BoardLogic.markPassedFreeSlots(
                slotRecords.mapNotNull { r ->
                    val isFree = r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] == null
                    val skipped = r[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null
                    when {
                        skipped -> null
                        isFree && !showBreaks -> null
                        else -> {
                            val finished = r.get("match_finished_at", LocalDateTime::class.java) != null
                            val active = r.get("match_started_at", LocalDateTime::class.java) != null ||
                                r[COMPETITION_MATCH.ACTIVATED_AT] != null
                            BoardProgramEntry(
                                startTime = r[EVENT_SCHEDULE_SLOT.START_TIME],
                                name = if (isFree) r[EVENT_SCHEDULE_SLOT.NAME] else null,
                                competitionName = r.get("competition_name", String::class.java),
                                competitionShortName = r.get("competition_short_name", String::class.java),
                                roundName = r.get("round_name", String::class.java),
                                matchName = r.get("match_name", String::class.java),
                                state = when {
                                    finished -> BoardProgramState.FINISHED
                                    active -> BoardProgramState.RUNNING
                                    else -> BoardProgramState.UPCOMING
                                },
                            )
                        }
                    }
                }
                )
            )
        }
}
