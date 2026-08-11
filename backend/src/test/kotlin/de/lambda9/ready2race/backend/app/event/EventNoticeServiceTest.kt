package de.lambda9.ready2race.backend.app.event

import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventNoticeDto
import de.lambda9.ready2race.backend.app.event.entity.EventNoticeSeverity
import de.lambda9.ready2race.backend.app.event.entity.UpdateEventNoticeRequest
import de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardService
import de.lambda9.ready2race.backend.app.liveDashboard.entity.LiveDashboardScope
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.ready2race.backend.app.JEnv
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Der Hinweis-Umlauf gegen ein echtes Postgres: setzen, überschreiben, löschen - und dass das
 * Schiedsrichter-Dashboard ihn eingebettet mitliefert. Die öffentlichen Anzeigen (Mein Event,
 * Board, Live-Tab) stehen in `EventNoticeInPublicViewsTest`, die HTTP-Leitung in
 * `EventNoticeHttpIT`.
 */
class EventNoticeServiceTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 11, 17, 0)

    private fun TestComprehensionScope<JEnv>.seedEvent(): UUID {
        val eventId = UUID.randomUUID()
        !EVENT.insert(EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now))
        return eventId
    }

    @Test
    fun settingOverwritingAndClearingTheNotice() = testComprehension {
        val eventId = seedEvent()

        // Frisch angelegt: kein Banner.
        assertNull(!EventRepo.getNotice(eventId))

        !EventService.updateEventNotice(
            eventId,
            SYSTEM_USER,
            UpdateEventNoticeRequest(text = "Sturmwarnung ab 14 Uhr", severity = "WARNING"),
        )
        assertEquals(
            EventNoticeDto("Sturmwarnung ab 14 Uhr", EventNoticeSeverity.WARNING),
            !EventRepo.getNotice(eventId),
        )

        // Überschreiben ersetzt Text und Stufe in einem Schritt.
        !EventService.updateEventNotice(
            eventId,
            SYSTEM_USER,
            UpdateEventNoticeRequest(text = "Regatta unterbrochen", severity = "CRITICAL"),
        )
        assertEquals(
            EventNoticeDto("Regatta unterbrochen", EventNoticeSeverity.CRITICAL),
            !EventRepo.getNotice(eventId),
        )

        // Beide null: Banner weg.
        !EventService.updateEventNotice(
            eventId,
            SYSTEM_USER,
            UpdateEventNoticeRequest(text = null, severity = null),
        )
        assertNull(!EventRepo.getNotice(eventId))
    }

    @Test
    fun theLiveDashboardCarriesTheNotice() = testComprehension {
        val eventId = seedEvent()

        !EventService.updateEventNotice(
            eventId,
            SYSTEM_USER,
            UpdateEventNoticeRequest(text = "Achterbahn gesperrt", severity = "INFO"),
        )

        val dashboard = (!LiveDashboardService.getLiveDashboard(eventId, LiveDashboardScope.ALL)).dto
        assertEquals(
            EventNoticeDto("Achterbahn gesperrt", EventNoticeSeverity.INFO),
            dashboard.notice,
        )

        !EventService.updateEventNotice(eventId, SYSTEM_USER, UpdateEventNoticeRequest(null, null))
        // Kein Zwischenspeicher am Dashboard (nur ETag): das Löschen ist sofort sichtbar.
        assertNull((!LiveDashboardService.getLiveDashboard(eventId, LiveDashboardScope.ALL)).dto.notice)
    }
}
