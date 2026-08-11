package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.event.boundary.EventService
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
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Der veranstaltungsweite Hinweis in den GEPOLLTEN öffentlichen Antworten: Mein Event,
 * Board-View und der Live-Tab der Ergebnisseite tragen ihn eingebettet - kein eigener Abruf,
 * der Banner kommt mit dem nächsten Poll.
 *
 * Der Hinweis wird jeweils VOR dem ersten Abruf gesetzt: alle drei Antworten liegen ein paar
 * Sekunden im Zwischenspeicher, und ein Test, der erst abruft und dann setzt, prüfte nur den
 * Cache statt der Einbettung.
 */
class EventNoticeInPublicViewsTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 11, 17, 0)

    private val notice = EventNoticeDto("Sturmwarnung ab 14 Uhr", EventNoticeSeverity.WARNING)

    private val request = UpdateEventNoticeRequest(text = notice.text, severity = notice.severity.name)

    @Test
    fun myEventCarriesTheNotice() = testComprehension {
        val fixture = !MyEventFixture.create()

        !EventService.updateEventNotice(fixture.eventId, SYSTEM_USER, request)

        val response = !MyEventService.getMyEvent(fixture.eventId, fixture.participantQrCode)
        assertEquals(notice, response.dto.notice)
    }

    @Test
    fun theBoardViewCarriesTheNotice() = testComprehension {
        val eventId = UUID.randomUUID()
        !EVENT.insert(EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now))
        val board = (!BoardService.createBoard(eventId, BoardRequest.example)).dto

        !EventService.updateEventNotice(eventId, SYSTEM_USER, request)

        val view = (!BoardService.getBoardView(eventId, board.id)).dto
        assertEquals(notice, view.notice)
    }

    @Test
    fun theLiveMatchesEnvelopeCarriesTheNotice() = testComprehension {
        val eventId = UUID.randomUUID()
        !EVENT.insert(EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now))

        !EventService.updateEventNotice(eventId, SYSTEM_USER, request)

        // Seit dem Umschlag (LiveMatchesDto) ist die Antwort ein Objekt, kein nacktes Array -
        // der Hinweis steht daneben, die Läufe unverändert darin.
        val response = (!EventInfoService.getLiveMatches(eventId, limit = 100)).dto
        assertEquals(notice, response.notice)
        assertEquals(emptyList(), response.matches)
    }
}
