package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerAssignmentPlan
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die „verschieben"-Regel beim Umdrehen der Zuordnung (am Rennen die Wettkämpfe anhaken).
 */
class RaceClockerAssignmentPlanTest {

    private val raceA = UUID.randomUUID()
    private val raceB = UUID.randomUUID()
    private val x = UUID.randomUUID()
    private val y = UUID.randomUUID()
    private val z = UUID.randomUUID()

    @Test
    fun anAssignedCompetitionThatInheritsIsSetToTheRace() {
        val changes = RaceClockerAssignmentPlan.changes(
            raceId = raceA,
            selected = setOf(x),
            current = mapOf(x to null),
        )
        assertEquals(mapOf(x to raceA), changes)
    }

    @Test
    fun checkingAtARaceMovesItAwayFromAnother() {
        // X hängt an B, wird bei A angehakt → wandert nach A. B wird nicht angefasst; X zeigt jetzt
        // schlicht auf A statt auf B.
        val changes = RaceClockerAssignmentPlan.changes(
            raceId = raceA,
            selected = setOf(x),
            current = mapOf(x to raceB),
        )
        assertEquals(mapOf(x to raceA), changes)
    }

    @Test
    fun uncheckingAtThisRaceFallsBackToInherit() {
        // X zeigt auf A, ist bei A nicht mehr angehakt → erbt wieder (null).
        val changes = RaceClockerAssignmentPlan.changes(
            raceId = raceA,
            selected = emptySet(),
            current = mapOf(x to raceA),
        )
        assertEquals(mapOf(x to null), changes)
    }

    @Test
    fun aCompetitionPointingElsewhereIsLeftAlone() {
        // Y zeigt auf B und ist bei A nicht angehakt → dieses Rennen geht Y nichts an, keine Änderung.
        val changes = RaceClockerAssignmentPlan.changes(
            raceId = raceA,
            selected = emptySet(),
            current = mapOf(y to raceB),
        )
        assertEquals(emptyMap(), changes)
    }

    @Test
    fun onlyRealChangesAreReturned() {
        // X schon auf A (bleibt), Y erbt und bleibt, Z wird neu auf A gesetzt.
        val changes = RaceClockerAssignmentPlan.changes(
            raceId = raceA,
            selected = setOf(x, z),
            current = mapOf(x to raceA, y to null, z to null),
        )
        assertEquals(mapOf(z to raceA), changes)
    }
}
