package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRef
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Welches Rennen für einen Lauf gilt.
 *
 * Seit dem 11.08.2026 hat ein Wettkampf genau EIN Rennen für alle seine Runden — die frühere
 * Weiche nach Rundenart samt Rückfall auf das jeweils andere Rennen ist entfallen. Übrig bleibt:
 * Rennen angewählt → genau eine Adresse; keines angewählt → nichts zu holen.
 */
class RaceClockerMatchTargetTest {

    private val shortCourse = RaceClockerRaceRef(UUID.randomUUID(), "Kurzstrecke", "https://raceclocker.com/kurz")

    @Test
    fun `ein angewähltes Rennen liefert genau eine Adresse`() {
        val t = RaceClockerMatchTarget(waveName = "Lauf 1", race = shortCourse)
        assertEquals(shortCourse.resultsUrl, t.resultsUrl)
        assertEquals(listOf(shortCourse.resultsUrl), t.candidateUrls)
        assertEquals(listOf("Kurzstrecke"), t.candidateRaceNames)
    }

    @Test
    fun `ohne Anwahl gibt es nichts zu holen`() {
        val t = RaceClockerMatchTarget(waveName = "Lauf 1", race = null)
        assertNull(t.resultsUrl)
        assertEquals(emptyList(), t.candidateUrls)
        assertEquals(emptyList(), t.candidateRaceNames)
    }
}
