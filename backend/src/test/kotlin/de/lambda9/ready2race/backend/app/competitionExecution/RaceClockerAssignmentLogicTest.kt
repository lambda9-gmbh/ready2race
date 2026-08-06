package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.boundary.RaceClockerAssignmentLogic
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchTeamWithRegistration
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Zuordnung von RaceClocker-Feed-Zeilen zu den Teams eines Laufs - herausgelöst aus
 * CompetitionExecutionService.assignFeedRows, damit sie ohne Datenbank prüfbar ist. Siehe die KDoc
 * dort (jetzt an RaceClockerAssignmentLogic.assignFeedRows) für die beiden Zuordnungswege und ihre
 * Reihenfolge.
 */
class RaceClockerAssignmentLogicTest {

    private fun team(
        id: UUID = UUID.randomUUID(),
        competitionRegistration: UUID = UUID.randomUUID(),
    ) = CompetitionMatchTeamWithRegistration(
        id = id,
        competitionMatch = UUID.randomUUID(),
        startNumber = 1,
        place = null,
        timeString = null,
        placesCalculated = false,
        competitionRegistration = competitionRegistration,
        clubId = UUID.randomUUID(),
        clubName = "Club",
        registrationName = null,
        teamNumber = null,
        participants = emptyList(),
        deregistered = false,
        deregistrationReason = null,
        out = false,
        failed = false,
        failedReason = null,
        penaltySeconds = null,
        penaltyNote = null,
        ratingCategory = null,
        mixedTeamTerm = null,
    )

    private fun row(
        ids: List<UUID> = emptyList(),
        wave: String? = null,
        name: String = "Boot",
    ) = RaceClockerFeedRow(
        name = name,
        rank = null,
        bib = null,
        wave = wave,
        ids = ids,
        result = null,
        start = null,
        penaltySeconds = null,
        penaltyNote = null,
    )

    @Test
    fun matchTeamIdMatchWinsRegardlessOfWave() {
        // (a) Ein Treffer über die Match-Team-ID gewinnt, egal was in der Welle steht - der
        // Registration-Fallback greift dann gar nicht erst.
        val t = team()
        val feedRow = row(ids = listOf(t.id), wave = "Irgendeine andere Welle", name = "Treffer")

        val result = RaceClockerAssignmentLogic.assignFeedRows(listOf(feedRow), listOf(t), waveName = "AF1 CM1x")

        assertEquals(mapOf(t.competitionRegistration to listOf(feedRow)), result)
    }

    @Test
    fun fallsBackToRegistrationIdRestrictedToTheWaveWhenItStillOccursInTheFeed() {
        // (b) Kein Match-Team-Treffer: der Registration-Fallback greift, aber nur auf Zeilen der
        // eigenen Welle - eine gleichnamige Registrierung in einer fremden Welle darf nicht
        // mitgezogen werden.
        val t = team()
        val ownWave = row(ids = listOf(t.competitionRegistration), wave = "AF1 CM1x", name = "eigene Welle")
        val otherWave = row(ids = listOf(t.competitionRegistration), wave = "AF2 CM1x", name = "fremde Welle")

        val result = RaceClockerAssignmentLogic.assignFeedRows(
            listOf(ownWave, otherWave),
            listOf(t),
            waveName = "AF1 CM1x",
        )

        assertEquals(mapOf(t.competitionRegistration to listOf(ownWave)), result)
    }

    @Test
    fun renamedWaveMakesEveryRowACandidateForTheRegistrationFallback() {
        // (c) Der exportierte Wellenname kommt im Feed nicht mehr vor (RaceClocker hat die Welle am
        // Renntag umbenannt/zusammengelegt) - dann zählt nur noch die Registration-ID, unabhängig
        // von der Welle der Zeile.
        val t = team()
        val feedRow = row(ids = listOf(t.competitionRegistration), wave = "AF1 & AF2", name = "umbenannt")

        val result = RaceClockerAssignmentLogic.assignFeedRows(
            listOf(feedRow),
            listOf(t),
            waveName = "AF1 CM1x",
        )

        assertEquals(mapOf(t.competitionRegistration to listOf(feedRow)), result)
    }

    @Test
    fun twoFeedRowsForTheSameRegistrationBothEndUpInTheList() {
        // (d) Ein doppelt importierter Start (RaceClocker ist insert-only) liefert zwei Zeilen für
        // dieselbe Registrierung - beide landen im Ergebnis, die Ablehnung als Duplikat passiert
        // erst downstream in CompetitionExecutionService.
        val t = team()
        val first = row(ids = listOf(t.competitionRegistration), name = "erster Import")
        val second = row(ids = listOf(t.competitionRegistration), name = "zweiter Import")

        val result = RaceClockerAssignmentLogic.assignFeedRows(listOf(first, second), listOf(t), waveName = null)

        assertEquals(listOf(first, second), result[t.competitionRegistration])
    }

    @Test
    fun teamsWithoutAMatchingRowAreAbsentFromTheResult() {
        // (e) filterValues: ein Team ohne Feed-Zeile taucht im Ergebnis gar nicht erst auf, statt mit
        // einer leeren Liste.
        val matched = team()
        val unmatched = team()
        val feedRow = row(ids = listOf(matched.competitionRegistration), name = "nur dieses Team")

        val result = RaceClockerAssignmentLogic.assignFeedRows(
            listOf(feedRow),
            listOf(matched, unmatched),
            waveName = null,
        )

        assertEquals(setOf(matched.competitionRegistration), result.keys)
        assertTrue(unmatched.competitionRegistration !in result)
    }

    @Test
    fun noRowsAtAllYieldAnEmptyMap() {
        val t = team()

        val result = RaceClockerAssignmentLogic.assignFeedRows(emptyList(), listOf(t), waveName = "AF1 CM1x")

        assertTrue(result.isEmpty())
    }
}
