package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventNoticeDto
import de.lambda9.ready2race.backend.app.event.entity.EventNoticeSeverity
import de.lambda9.ready2race.backend.app.event.entity.UpdateEventNoticeRequest
import de.lambda9.ready2race.backend.app.eventInfo.boundary.BoardService
import de.lambda9.ready2race.backend.app.eventInfo.boundary.EventInfoService
import de.lambda9.ready2race.backend.app.eventInfo.boundary.MyEventService
import de.lambda9.ready2race.backend.app.eventInfo.entity.BoardRequest
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Die Zwischenspeicher der öffentlichen Anzeigen werden beim Schreiben entwertet
 * (EventChangeMarker): eine Änderung erscheint mit dem NÄCHSTEN Abruf, nicht erst nach Ablauf
 * der TTL. Der Hinweis-PUT ist hier stellvertretend die Schreibaktion - er ist der leichteste
 * Weg, eine sichtbare Änderung in allen drei gepollten Antworten zu erzeugen.
 *
 * Die Reihenfolge ist der Punkt jedes Tests: ERST abrufen (Cache warm), DANN schreiben, dann
 * sofort wieder abrufen. Vor der Invalidierung war genau diese Reihenfolge unmöglich zu testen
 * (siehe den Hinweis in EventNoticeInPublicViewsTest) - jetzt ist sie das Sollverhalten.
 */
class EventChangeInvalidationTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 12, 9, 0)

    private val notice = EventNoticeDto("Startverschiebung: Strömung", EventNoticeSeverity.WARNING)

    private val request = UpdateEventNoticeRequest(text = notice.text, severity = notice.severity.name)

    @Test
    fun aWriteInvalidatesTheBoardViewImmediately() = testComprehension {
        val eventId = UUID.randomUUID()
        !EVENT.insert(EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now))
        val board = (!BoardService.createBoard(eventId, BoardRequest.example)).dto

        // Cache warm machen: der erste Abruf baut den Eintrag, noch ohne Hinweis.
        val first = (!BoardService.getBoardView(eventId, board.id)).dto
        assertNull(first.notice)

        !EventService.updateEventNotice(eventId, SYSTEM_USER, request)

        // Sofortiger zweiter Abruf, deutlich innerhalb der TTL: der neue Stand, nicht der Eintrag.
        val second = (!BoardService.getBoardView(eventId, board.id)).dto
        assertEquals(notice, second.notice)
    }

    @Test
    fun withoutAWriteTheCachedBoardViewIsServed() = testComprehension {
        val eventId = UUID.randomUUID()
        !EVENT.insert(EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now))
        val board = (!BoardService.createBoard(eventId, BoardRequest.example)).dto

        !EventService.updateEventNotice(eventId, SYSTEM_USER, request)
        val first = (!BoardService.getBoardView(eventId, board.id)).dto
        assertEquals(notice, first.notice)

        // Die Gegenprobe zur Invalidierung: eine Änderung am Datensatz OHNE Bump - so sieht der
        // Ruhezustand für den Marker aus. Innerhalb der TTL muss weiter der Cache-Eintrag kommen,
        // sonst wäre die Datenbank-Schonung des Zwischenspeichers verloren (Mehrlast je Poll).
        val record = !EventRepo.get(eventId).orDie()
        !EventRepo.update(record!!) {
            noticeText = "heimlich geändert - darf im Cache-Fenster nicht erscheinen"
        }.orDie()

        val second = (!BoardService.getBoardView(eventId, board.id)).dto
        assertEquals(notice, second.notice)
    }

    @Test
    fun aWriteInvalidatesMyEventImmediately() = testComprehension {
        val fixture = !MyEventFixture.create()

        val first = (!MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)).dto
        assertNull(first.notice)

        !EventService.updateEventNotice(fixture.eventId, SYSTEM_USER, request)

        val second = (!MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)).dto
        assertEquals(notice, second.notice)
    }

    @Test
    fun aWriteInvalidatesTheLiveMatchesImmediately() = testComprehension {
        val eventId = UUID.randomUUID()
        !EVENT.insert(EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now))

        val first = (!EventInfoService.getLiveMatches(eventId, limit = 100)).dto
        assertNull(first.notice)

        !EventService.updateEventNotice(eventId, SYSTEM_USER, request)

        val second = (!EventInfoService.getLiveMatches(eventId, limit = 100)).dto
        assertEquals(notice, second.notice)
    }
}
