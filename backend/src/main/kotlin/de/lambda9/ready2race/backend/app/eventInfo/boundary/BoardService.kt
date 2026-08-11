package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.BoardRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.toAthleteBoardMatch
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
    private data class CachedView(val builtAt: LocalDateTime, val dto: BoardViewDto)

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

            val cached = boardViewCache[boardId]?.takeIf { BoardLogic.isCacheFresh(it.builtAt, now) }
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
                var running = (!EventInfoService.getRunningMatches(eventId, needs.runningLimit, clubShortNames))
                    .data.map { it.toAthleteBoardMatch(now, showCountdown = true, includeDetails = details) }
                var upcoming = AthleteBoardLogic.sortByStartTime(
                    (!EventInfoService.getUpcomingMatchesForBoard(eventId, needs.upcomingLimit, clubShortNames))
                        .data.map { it.toAthleteBoardMatch(now, showCountdown = true, includeDetails = details) }
                ) { it.startTime }
                val results =
                    (!EventInfoService.getLatestMatchResults(eventId, needs.resultsLimit, null, clubShortNames))
                        .data.map { it.toAthleteBoardResult() }

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

                // Ehrungen sind bewusst fehlertolerant: eine veraltete Kachel-Konfiguration
                // darf das Board nicht mitreißen (siehe boardCeremonies).
                val ceremonies = !AwardCeremonyService.boardCeremonies(eventId, needs.ceremonies)
                    .recover { KIO.ok(emptyList()) }

                val program = if (needs.schedule) !buildProgram(eventId) else emptyList()

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
                )

                boardViewCache[boardId] = CachedView(now, dto)
                KIO.ok(ApiResponse.Dto(dto))
            }
        }

    /**
     * Das Tagesprogramm aus dem Zeitplan: jeder Slot mit seinem Zustand. Entfallene
     * Slots bleiben draußen; Pausen (FREE-Slots) nur, wenn die Veranstaltung sie auf
     * öffentlichen Anzeigen zeigt — dieselbe Regel wie bei den Platzhaltern der
     * Lauf-Blöcke (EventInfoService.mergeWithPendingPlaceholders).
     */
    private fun buildProgram(eventId: UUID): App<EventInfoProblem, List<BoardProgramEntry>> =
        KIO.comprehension {
            val showBreaks = !EventRepo.getShowBreaksOnPublicBoards(eventId).orDie()
            val slotRecords = !EventScheduleRepo.getSlots(eventId).orDie()

            KIO.ok(
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
        }
}
