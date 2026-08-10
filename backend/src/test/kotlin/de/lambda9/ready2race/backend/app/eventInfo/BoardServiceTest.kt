package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.eventInfo.boundary.BoardService
import de.lambda9.ready2race.backend.app.eventInfo.entity.*
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [BoardService] gegen ein echtes Postgres: der Umlauf der Verwaltungsmaske (anlegen,
 * umbenennen, löschen) und die View-Auflösung an einem leeren Event — die Slots müssen
 * stehen und leer sein, statt zu fehlen (die Struktur eines montierten Bildschirms
 * bleibt, auch wenn nichts fährt).
 */
class BoardServiceTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 10, 12, 0)

    @Test
    fun boardRoundTripAndViewResolution() = testComprehension {
        val eventId = UUID.randomUUID()
        !EVENT.insert(EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now))

        val created = (!BoardService.createBoard(eventId, BoardRequest.example)).dto

        val listed = (!BoardService.getBoards(eventId)).data
        assertEquals(listOf(created.id), listed.map { it.id })
        assertEquals(BoardLayout.THREE_COLUMNS, listed.single().config.layout)

        val names = (!BoardService.getBoardNames(eventId)).data
        assertEquals(listOf(created.id), names.map { it.id })

        // View am leeren Event: alle konfigurierten Offsets sind da, aber unbesetzt.
        val view = (!BoardService.getBoardView(eventId, created.id)).dto
        assertEquals(setOf(0, 1, -1), view.slots.map { it.offset }.toSet())
        assertTrue(view.slots.all { it.match == null && it.result == null })
        assertEquals("Testregatta", view.eventName)
        assertEquals(BoardLimits.DEFAULT_REFRESH_INTERVAL_SECONDS, view.refreshIntervalSeconds)

        // Update ändert den Namen; der View-Cache wird invalidiert (sichtbar daran, dass
        // die nächste Antwort die neue Konfiguration trägt).
        !BoardService.updateBoard(
            created.id,
            BoardRequest.example.copy(
                name = "Zielturm",
                config = BoardRequest.example.config.copy(refreshIntervalSeconds = 30),
            ),
        )
        assertEquals("Zielturm", (!BoardService.getBoards(eventId)).data.single().name)
        assertEquals(30, (!BoardService.getBoardView(eventId, created.id)).dto.refreshIntervalSeconds)

        !BoardService.deleteBoard(created.id)
        assertTrue((!BoardService.getBoards(eventId)).data.isEmpty())
    }
}
