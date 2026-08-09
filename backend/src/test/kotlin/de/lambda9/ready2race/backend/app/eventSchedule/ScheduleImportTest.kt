package de.lambda9.ready2race.backend.app.eventSchedule

import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ImportCandidate
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ScheduleImport
import de.lambda9.ready2race.backend.app.eventSchedule.entity.ImportRowStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleImportTest {

    private val af1DM = ImportCandidate(UUID.randomUUID(), setOf("cm 1x", "männer-einer", "12"), "AF1", "Achtelfinale")
    private val af1Int = ImportCandidate(UUID.randomUUID(), setOf("cm 1x", "männer-einer international", "012-int"), "AF1", "Achtelfinale")
    private val finaleA = ImportCandidate(UUID.randomUUID(), setOf("cmix 4x+", "mixed-doppelvierer", "18"), "Finale A", "Finale")
    private val finaleB = ImportCandidate(UUID.randomUUID(), setOf("cmix 4x+", "mixed-doppelvierer", "18"), "Finale B", "Finale")

    @Test
    fun exactCompetitionAndMatchNameLinks() {
        val result = ScheduleImport.matchRow("CMix 4x+", "Finale A", listOf(af1DM, finaleA))

        assertEquals(ImportRowStatus.LINKED, result.status)
        assertEquals(finaleA.setupMatchId, result.setupMatchId)
    }

    @Test
    fun matchingIsCaseAndWhitespaceInsensitive() {
        val result = ScheduleImport.matchRow("  cmix 4X+ ", " finale a ", listOf(finaleA))

        assertEquals(ImportRowStatus.LINKED, result.status)
        assertEquals(finaleA.setupMatchId, result.setupMatchId)
    }

    @Test
    fun emptyCompetitionMeansFreeSlot() {
        assertEquals(ImportRowStatus.FREE, ScheduleImport.matchRow(null, "Mittagspause", listOf(finaleA)).status)
        assertEquals(ImportRowStatus.FREE, ScheduleImport.matchRow("  ", "Siegerehrung", listOf(finaleA)).status)
    }

    @Test
    fun unknownCompetitionIsReportedAsSuch() {
        val result = ScheduleImport.matchRow("CF 8x", "Finale A", listOf(finaleA))

        assertEquals(ImportRowStatus.COMPETITION_NOT_FOUND, result.status)
        assertNull(result.setupMatchId)
    }

    @Test
    fun knownCompetitionWithUnknownMatchNameListsTheAvailableOnes() {
        val result = ScheduleImport.matchRow("CMix 4x+", "Vorlauf 1", listOf(af1DM, finaleA, finaleB))

        assertEquals(ImportRowStatus.MATCH_NOT_FOUND, result.status)
        assertEquals(listOf("Finale A", "Finale B"), result.availableMatches)
    }

    @Test
    fun availableMatchesAreOnlyFilledForTheUnknownMatchCase() {
        assertEquals(emptyList(), ScheduleImport.matchRow("CMix 4x+", "Finale A", listOf(finaleA)).availableMatches)
        assertEquals(emptyList(), ScheduleImport.matchRow("CF 8x", "Finale A", listOf(finaleA)).availableMatches)
        assertEquals(emptyList(), ScheduleImport.matchRow(null, "Pause", listOf(finaleA)).availableMatches)
    }

    @Test
    fun twoCompetitionsSharingTheTextAreAmbiguous() {
        val result = ScheduleImport.matchRow("CM 1x", "AF1", listOf(af1DM, af1Int))

        assertEquals(ImportRowStatus.AMBIGUOUS, result.status)
        assertNull(result.setupMatchId)
    }
}
