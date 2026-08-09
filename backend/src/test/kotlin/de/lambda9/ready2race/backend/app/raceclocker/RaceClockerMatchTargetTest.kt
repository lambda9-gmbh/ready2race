package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRef
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Welches Rennen für einen Lauf gilt — und welches der Rückfall ist.
 *
 * Die Runde entscheidet, welches Rennen ZUERST versucht wird. Sie ist eine Angabe, keine Garantie:
 * Eine als Zeitfahren gefahrene, aber nicht als Qualifikation markierte Runde findet ihren Lauf im
 * anderen Rennen. Deshalb bleibt der Rückfall — nur wird er ab jetzt erst bei Bedarf geholt.
 */
class RaceClockerMatchTargetTest {

    private val timeTrials = RaceClockerRaceRef(UUID.randomUUID(), "Timetrials", "https://raceclocker.com/tt")
    private val shortCourse = RaceClockerRaceRef(UUID.randomUUID(), "Kurzstrecke", "https://raceclocker.com/kurz")

    private fun target(
        isQualification: Boolean,
        qualificationRace: RaceClockerRaceRef? = timeTrials,
        roundsRace: RaceClockerRaceRef? = shortCourse,
    ) = RaceClockerMatchTarget(
        waveName = "Lauf 1",
        isQualification = isQualification,
        qualificationRace = qualificationRace,
        roundsRace = roundsRace,
    )

    @Test
    fun `eine Qualifikationsrunde beginnt beim Zeitfahren-Rennen`() {
        val t = target(isQualification = true)
        assertEquals(timeTrials.resultsUrl, t.resultsUrl)
        assertEquals(shortCourse.resultsUrl, t.alternateResultsUrl)
        assertEquals(listOf(timeTrials.resultsUrl, shortCourse.resultsUrl), t.candidateUrls)
        assertEquals(listOf("Timetrials", "Kurzstrecke"), t.candidateRaceNames)
    }

    @Test
    fun `jede andere Runde beginnt beim Laeufe-Rennen`() {
        val t = target(isQualification = false)
        assertEquals(shortCourse.resultsUrl, t.resultsUrl)
        assertEquals(timeTrials.resultsUrl, t.alternateResultsUrl)
        assertEquals(listOf("Kurzstrecke", "Timetrials"), t.candidateRaceNames)
    }

    /**
     * Eine Qualifikationsrunde ohne angewähltes Zeitfahren-Rennen hat kein erstes Rennen — wohl aber
     * einen Rückfall. Für den Abruf zählt allein [RaceClockerMatchTarget.candidateUrls]: Der Lauf
     * wird im Läufe-Rennen gesucht und dort auch gefunden. Genau dieser Fall ist der Normalzustand
     * einer Regatta ohne Zeitfahren, und er darf nicht zu „nichts zu holen" führen.
     */
    @Test
    fun `ohne Quali-Anwahl wird das Laeufe-Rennen zum Rueckfall`() {
        val t = target(isQualification = true, qualificationRace = null)
        assertNull(t.resultsUrl)
        assertEquals(shortCourse.resultsUrl, t.alternateResultsUrl)
        assertEquals(listOf(shortCourse.resultsUrl), t.candidateUrls)
        assertEquals(listOf("Kurzstrecke"), t.candidateRaceNames)
    }

    @Test
    fun `ohne jede Anwahl gibt es nichts zu holen`() {
        val t = target(isQualification = false, qualificationRace = null, roundsRace = null)
        assertNull(t.resultsUrl)
        assertEquals(emptyList(), t.candidateUrls)
        assertEquals(emptyList(), t.candidateRaceNames)
    }

    @Test
    fun `dasselbe Rennen fuer beides wird nur einmal geholt`() {
        val t = target(isQualification = true, qualificationRace = shortCourse, roundsRace = shortCourse)
        assertEquals(listOf(shortCourse.resultsUrl), t.candidateUrls)
        assertEquals(listOf("Kurzstrecke"), t.candidateRaceNames)
    }

    /**
     * Zwei verschiedene Rennen, die auf dieselbe Adresse zeigen, kann es je Veranstaltung nicht
     * geben — `uq_raceclocker_race_event_url` verbietet es. Der Rückfall entdoppelt trotzdem über
     * die Adresse und nicht über die Kennung, weil geholt wird, was die Adresse hergibt.
     */
    @Test
    fun `gleiche Adresse unter anderem Namen zaehlt als dasselbe Rennen`() {
        val twin = RaceClockerRaceRef(UUID.randomUUID(), "Kurzstrecke B", shortCourse.resultsUrl)
        val t = target(isQualification = false, qualificationRace = twin, roundsRace = shortCourse)
        assertEquals(listOf(shortCourse.resultsUrl), t.candidateUrls)
        assertEquals(listOf("Kurzstrecke"), t.candidateRaceNames)
    }
}
