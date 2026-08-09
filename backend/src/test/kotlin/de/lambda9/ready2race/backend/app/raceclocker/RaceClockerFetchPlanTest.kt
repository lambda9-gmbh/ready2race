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
 * Das ist der Kern dieser Änderung: Bisher holte der Job für jeden beobachteten Lauf BEIDE Adressen
 * — die angewählte und den Rückfall. Bei einer Regatta, die nur Läufe fährt, war damit jeder zweite
 * Abruf überflüssig, dauerhaft, im Fünf-Sekunden-Takt. Diese Tests halten fest, dass die zweite
 * Runde nur noch für Läufe stattfindet, die in ihrem Rennen nicht gefunden wurden.
 */
class RaceClockerFetchPlanTest {

    private val timeTrials = RaceClockerRaceRef(UUID.randomUUID(), "Timetrials", "https://raceclocker.com/tt")
    private val shortCourse = RaceClockerRaceRef(UUID.randomUUID(), "Kurzstrecke", "https://raceclocker.com/kurz")
    private val longCourse = RaceClockerRaceRef(UUID.randomUUID(), "Langstrecke", "https://raceclocker.com/lang")

    private fun target(
        isQualification: Boolean = false,
        qualificationRace: RaceClockerRaceRef? = timeTrials,
        roundsRace: RaceClockerRaceRef? = shortCourse,
    ) = RaceClockerMatchTarget("Lauf 1", isQualification, qualificationRace, roundsRace)

    @Test
    fun `Runde 1 holt nur die angewaehlten Rennen`() {
        val targets = listOf(target(), target(), target(roundsRace = longCourse))

        assertEquals(
            listOf(shortCourse.resultsUrl, longCourse.resultsUrl),
            RaceClockerFeedAssignment.primaryUrls(targets),
        )
    }

    /** Acht Läufe desselben Rennens kosten einen Abruf, nicht acht. */
    @Test
    fun `Runde 1 holt dieselbe Adresse nur einmal`() {
        val targets = List(8) { target() }

        assertEquals(listOf(shortCourse.resultsUrl), RaceClockerFeedAssignment.primaryUrls(targets))
    }

    @Test
    fun `Laeufe ohne Anwahl tragen nichts bei`() {
        val targets = listOf(target(qualificationRace = null, roundsRace = null))

        assertEquals(emptyList(), RaceClockerFeedAssignment.primaryUrls(targets))
    }

    /**
     * Eine Qualifikationsrunde ohne Zeitfahren-Rennen hat kein erstes Rennen. Sie darf trotzdem
     * nicht durchfallen — ihr Rückfall wird in Runde 2 geholt.
     */
    @Test
    fun `ein Lauf ohne erstes Rennen traegt zu Runde 1 nichts bei, wohl aber zu Runde 2`() {
        val t = target(isQualification = true, qualificationRace = null)

        assertEquals(emptyList(), RaceClockerFeedAssignment.primaryUrls(listOf(t)))
        assertEquals(
            listOf(shortCourse.resultsUrl),
            RaceClockerFeedAssignment.fallbackUrls(listOf(t), emptySet()),
        )
    }

    @Test
    fun `Runde 2 holt nur den Rueckfall der nicht gefundenen Laeufe`() {
        val unresolved = listOf(target())

        assertEquals(
            listOf(timeTrials.resultsUrl),
            RaceClockerFeedAssignment.fallbackUrls(unresolved, setOf(shortCourse.resultsUrl)),
        )
    }

    @Test
    fun `Runde 2 holt nichts erneut, was Runde 1 schon hat`() {
        val unresolved = listOf(target())

        assertEquals(
            emptyList(),
            RaceClockerFeedAssignment.fallbackUrls(
                unresolved,
                setOf(shortCourse.resultsUrl, timeTrials.resultsUrl),
            ),
        )
    }

    @Test
    fun `ein Lauf ohne Rueckfall loest keine zweite Runde aus`() {
        val unresolved = listOf(target(qualificationRace = null))

        assertEquals(
            emptyList(),
            RaceClockerFeedAssignment.fallbackUrls(unresolved, setOf(shortCourse.resultsUrl)),
        )
    }

    /** Zwei nicht gefundene Läufe mit demselben Rückfall kosten einen Abruf, nicht zwei. */
    @Test
    fun `Runde 2 entdoppelt ebenfalls`() {
        val unresolved = listOf(target(), target())

        assertEquals(
            listOf(timeTrials.resultsUrl),
            RaceClockerFeedAssignment.fallbackUrls(unresolved, setOf(shortCourse.resultsUrl)),
        )
    }
}
