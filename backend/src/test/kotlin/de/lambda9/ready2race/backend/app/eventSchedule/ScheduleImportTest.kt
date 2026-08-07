package de.lambda9.ready2race.backend.app.eventSchedule

import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ImportCandidate
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ScheduleImport
import de.lambda9.ready2race.backend.app.eventSchedule.entity.ImportRowStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleImportTest {

    private val af1DM = ImportCandidate(UUID.randomUUID(), setOf("cm 1x", "männer-einer", "12"), "AF1", "Achtelfinale")
    private val af1Int = ImportCandidate(UUID.randomUUID(), setOf("cm 1x", "männer-einer international", "012-int"), "AF1", "Achtelfinale")
    private val finaleA = ImportCandidate(UUID.randomUUID(), setOf("cmix 4x+", "mixed-doppelvierer", "18"), "Finale A", "Finale")

    @Test
    fun exactCompetitionAndMatchNameLinks() {
        assertEquals(
            ImportRowStatus.LINKED to finaleA.setupMatchId,
            ScheduleImport.matchRow("CMix 4x+", "Finale A", listOf(af1DM, finaleA)),
        )
    }

    @Test
    fun matchingIsCaseAndWhitespaceInsensitive() {
        assertEquals(
            ImportRowStatus.LINKED to finaleA.setupMatchId,
            ScheduleImport.matchRow("  cmix 4X+ ", " finale a ", listOf(finaleA)),
        )
    }

    @Test
    fun emptyCompetitionMeansFreeSlot() {
        assertEquals(ImportRowStatus.FREE to null, ScheduleImport.matchRow(null, "Mittagspause", listOf(finaleA)))
        assertEquals(ImportRowStatus.FREE to null, ScheduleImport.matchRow("  ", "Siegerehrung", listOf(finaleA)))
    }

    @Test
    fun noHitFallsBackToFree() {
        assertEquals(ImportRowStatus.FREE to null, ScheduleImport.matchRow("CF 8x", "Finale A", listOf(finaleA)))
    }

    @Test
    fun twoCompetitionsSharingTheTextAreAmbiguous() {
        assertEquals(
            ImportRowStatus.AMBIGUOUS to null,
            ScheduleImport.matchRow("CM 1x", "AF1", listOf(af1DM, af1Int)),
        )
    }
}
