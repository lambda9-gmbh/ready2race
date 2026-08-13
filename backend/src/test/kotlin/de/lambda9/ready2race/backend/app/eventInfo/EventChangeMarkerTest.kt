package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.eventInfo.boundary.EventChangeMarker
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reine Tests des Änderungsmarkers - keine Datenbank, keine Umgebung. Der Marker ist ein
 * globales Singleton; die Tests arbeiten deshalb mit frischen Zufalls-Ids und vergleichen
 * relativ zum Ausgangsstand statt gegen absolute Werte.
 */
class EventChangeMarkerTest {

    @Test
    fun `eine unbekannte Veranstaltung steht auf 0`() {
        assertEquals(0L, EventChangeMarker.current(UUID.randomUUID()))
    }

    @Test
    fun `bump erhöht den Stand monoton`() {
        val eventId = UUID.randomUUID()
        val before = EventChangeMarker.current(eventId)

        EventChangeMarker.bump(eventId)
        assertEquals(before + 1, EventChangeMarker.current(eventId))

        EventChangeMarker.bump(eventId)
        assertEquals(before + 2, EventChangeMarker.current(eventId))
    }

    @Test
    fun `Veranstaltungen zählen unabhängig voneinander`() {
        val bumped = UUID.randomUUID()
        val untouched = UUID.randomUUID()

        EventChangeMarker.bump(bumped)

        assertEquals(1L, EventChangeMarker.current(bumped))
        assertEquals(0L, EventChangeMarker.current(untouched))
    }

    @Test
    fun `current verändert den Stand nicht`() {
        val eventId = UUID.randomUUID()
        EventChangeMarker.bump(eventId)

        // Lesen ist frei von Nebenwirkungen - genau darauf verlassen sich die Cache-Prüfungen,
        // die je Abruf einmal lesen.
        repeat(3) { EventChangeMarker.current(eventId) }
        assertEquals(1L, EventChangeMarker.current(eventId))
    }
}
