package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.BoardRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.toAthleteBoardMatch
import de.lambda9.ready2race.backend.app.eventInfo.control.toAthleteBoardResult
import de.lambda9.ready2race.backend.app.eventInfo.control.toDto
import de.lambda9.ready2race.backend.app.eventInfo.control.toJsonb
import de.lambda9.ready2race.backend.app.eventInfo.control.toNameDto
import de.lambda9.ready2race.backend.app.eventInfo.control.toRecord
import de.lambda9.ready2race.backend.app.eventInfo.entity.*
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
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

            val cached = boardViewCache[boardId]?.takeIf { AthleteBoardLogic.isCacheFresh(it.builtAt, now) }
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
                // aufbaut.
                val running = (!EventInfoService.getRunningMatches(eventId, needs.runningLimit, clubShortNames))
                    .data.map { it.toAthleteBoardMatch(now, showCountdown = true) }
                val upcoming = AthleteBoardLogic.sortByStartTime(
                    (!EventInfoService.getUpcomingMatchesForBoard(eventId, needs.upcomingLimit, clubShortNames))
                        .data.map { it.toAthleteBoardMatch(now, showCountdown = true) }
                ) { it.startTime }
                val results =
                    (!EventInfoService.getLatestMatchResults(eventId, needs.resultsLimit, null, clubShortNames))
                        .data.map { it.toAthleteBoardResult() }

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
                        }
                    },
                )

                boardViewCache[boardId] = CachedView(now, dto)
                KIO.ok(ApiResponse.Dto(dto))
            }
        }
}
