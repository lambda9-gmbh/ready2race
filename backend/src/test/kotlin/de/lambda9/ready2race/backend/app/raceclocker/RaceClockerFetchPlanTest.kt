package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerFeedAssignment
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRef
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Was ein Takt tatsächlich anfragt.
 *
 * Seit jeder Wettkampf genau ein Rennen hat (11.08.2026), gibt es keine Rückfall-Runde mehr —
 * geholt werden die angewählten Rennen, entdoppelt. Diese Tests halten fest, dass ein Rennen mit
 * vielen beobachteten Läufen trotzdem nur einen Abruf je Takt kostet.
 */
class RaceClockerFetchPlanTest {

    private val shortCourse = RaceClockerRaceRef(UUID.randomUUID(), "Kurzstrecke", "https://raceclocker.com/kurz")
    private val longCourse = RaceClockerRaceRef(UUID.randomUUID(), "Langstrecke", "https://raceclocker.com/lang")

    private fun target(race: RaceClockerRaceRef? = shortCourse) =
        RaceClockerMatchTarget("Lauf 1", race)

    @Test
    fun `ein Takt holt nur die angewählten Rennen`() {
        val targets = listOf(target(), target(), target(race = longCourse))

        assertEquals(
            listOf(shortCourse.resultsUrl, longCourse.resultsUrl),
            RaceClockerFeedAssignment.urls(targets),
        )
    }

    /** Acht Läufe desselben Rennens kosten einen Abruf, nicht acht. */
    @Test
    fun `dieselbe Adresse wird nur einmal geholt`() {
        val targets = List(8) { target() }

        assertEquals(listOf(shortCourse.resultsUrl), RaceClockerFeedAssignment.urls(targets))
    }

    @Test
    fun `Läufe ohne Anwahl tragen nichts bei`() {
        val targets = listOf(target(race = null))

        assertEquals(emptyList(), RaceClockerFeedAssignment.urls(targets))
    }
}
