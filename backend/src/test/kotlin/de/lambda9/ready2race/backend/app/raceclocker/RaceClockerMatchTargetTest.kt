package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die URL-Auswahl für einen RaceClocker-Pull. Hat dieselbe Form wie StartListConfigTargetTest und
 * denselben Grund: RaceClocker braucht pro Wettkampf zwei Rennen (Zeitfahren- und Läufe-Race), und
 * [RaceClockerMatchTarget.isQualification] entscheidet nur, welches zuerst versucht wird - candidateUrls
 * fällt danach auf das jeweils andere zurück, statt bei einer leeren primären URL aufzugeben.
 */
class RaceClockerMatchTargetTest {

    private val timeTrialUrl = "https://raceclocker.com/timetrial"
    private val heatsUrl = "https://raceclocker.com/heats"

    @Test
    fun qualificationRoundPrefersTheTimeTrialUrl() {
        val target = RaceClockerMatchTarget(
            waveName = null,
            isQualification = true,
            timeTrialUrl = timeTrialUrl,
            heatsUrl = heatsUrl,
        )

        assertEquals(timeTrialUrl, target.resultsUrl)
        assertEquals(heatsUrl, target.alternateResultsUrl)
        assertEquals(listOf(timeTrialUrl, heatsUrl), target.candidateUrls)
    }

    @Test
    fun otherRoundsPreferTheHeatsUrl() {
        val target = RaceClockerMatchTarget(
            waveName = "AF1 CM1x",
            isQualification = false,
            timeTrialUrl = timeTrialUrl,
            heatsUrl = heatsUrl,
        )

        assertEquals(heatsUrl, target.resultsUrl)
        assertEquals(timeTrialUrl, target.alternateResultsUrl)
        assertEquals(listOf(heatsUrl, timeTrialUrl), target.candidateUrls)
    }

    @Test
    fun qualificationWithoutATimeTrialUrlFallsBackToTheHeatsUrl() {
        // Das Zeitfahren-Ergebnisrennen ist nicht konfiguriert - resultsUrl bleibt aus, aber
        // candidateUrls liefert trotzdem noch das Läufe-Rennen als einzigen Kandidaten.
        val target = RaceClockerMatchTarget(
            waveName = null,
            isQualification = true,
            timeTrialUrl = null,
            heatsUrl = heatsUrl,
        )

        assertNull(target.resultsUrl)
        assertEquals(heatsUrl, target.alternateResultsUrl)
        assertEquals(listOf(heatsUrl), target.candidateUrls)
    }

    @Test
    fun blankUrlsCountAsNotConfigured() {
        // takeIf { isNotBlank() }: eine leere oder nur aus Leerraum bestehende URL zaehlt wie "nicht
        // konfiguriert", nicht wie eine echte (kaputte) URL.
        val target = RaceClockerMatchTarget(
            waveName = null,
            isQualification = true,
            timeTrialUrl = "   ",
            heatsUrl = heatsUrl,
        )

        assertNull(target.resultsUrl)
        assertEquals(heatsUrl, target.alternateResultsUrl)
        assertEquals(listOf(heatsUrl), target.candidateUrls)
    }

    @Test
    fun bothUrlsMissingYieldsNoCandidates() {
        val target = RaceClockerMatchTarget(
            waveName = null,
            isQualification = false,
            timeTrialUrl = null,
            heatsUrl = "",
        )

        assertNull(target.resultsUrl)
        assertNull(target.alternateResultsUrl)
        assertTrue(target.candidateUrls.isEmpty())
    }
}
