package de.lambda9.ready2race.backend.app.matchStatus

import de.lambda9.ready2race.backend.app.matchStatus.boundary.MatchStatusLogic
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchState
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchStatusDto
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchStatusTeam
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchStatusLogicTest {

    private val start = LocalDateTime.of(2026, 8, 7, 10, 0)

    private fun open() = MatchStatusTeam(place = null, failed = false, deregistered = false)
    private fun placed(place: Int) = MatchStatusTeam(place = place, failed = false, deregistered = false)
    private fun failed() = MatchStatusTeam(place = null, failed = true, deregistered = false)
    private fun deregistered() = MatchStatusTeam(place = null, failed = false, deregistered = true)

    // --- scoredCount ---

    @Test
    fun noTeamsScoresNothing() {
        assertEquals(0, MatchStatusLogic.scoredCount(emptyList()))
    }

    @Test
    fun onlyTeamsWithAPlaceCount() {
        assertEquals(2, MatchStatusLogic.scoredCount(listOf(placed(1), placed(2), open(), open())))
    }

    /** Für eine abgemeldete Mannschaft kommt kein Ergebnis mehr - sie gilt als erledigt. */
    @Test
    fun deregisteredCountsAsScored() {
        assertEquals(2, MatchStatusLogic.scoredCount(listOf(placed(1), deregistered())))
    }

    /** Ausgeschieden ist ebenfalls ein Ergebnis, nur eben ohne Platz. */
    @Test
    fun failedCountsAsScored() {
        assertEquals(2, MatchStatusLogic.scoredCount(listOf(placed(1), failed())))
    }

    @Test
    fun scoredCountMatchesLiveDashboardRule() {
        val teams = listOf(placed(1), failed(), deregistered(), open())
        assertEquals(3, MatchStatusLogic.scoredCount(teams))
    }

    // --- matchStatus ---

    @Test
    fun runningBeatsEverythingElse() {
        val status = MatchStatusLogic.matchStatus(
            currentlyRunning = true,
            startTime = start,
            startedAt = start.plusMinutes(2),
            finishedAt = null,
            skipped = true,
            teams = listOf(placed(1), placed(2)),
        )
        // Was tatsächlich passiert, schlägt den zurückgenommenen Plan - siehe deriveMatchState.
        assertEquals(MatchState.RUNNING, status.state)
        assertEquals(start.plusMinutes(2), status.startedAt)
    }

    @Test
    fun finishedOnlyMeansFinishedAtIsSet() {
        val status = MatchStatusLogic.matchStatus(
            currentlyRunning = false,
            startTime = start,
            startedAt = start,
            finishedAt = start.plusMinutes(6),
            skipped = false,
            teams = listOf(placed(1), open()),
        )
        assertEquals(MatchState.FINISHED, status.state)
    }

    @Test
    fun fullyScoredButNotFinishedAwaitsFinish() {
        val status = MatchStatusLogic.matchStatus(
            currentlyRunning = false,
            startTime = start,
            startedAt = start,
            finishedAt = null,
            skipped = false,
            teams = listOf(placed(1), deregistered()),
        )
        assertEquals(MatchState.AWAITING_FINISH, status.state)
        assertEquals(2, status.teamsTotal)
        assertEquals(2, status.teamsScored)
    }

    @Test
    fun partiallyScoredStaysUpcoming() {
        val status = MatchStatusLogic.matchStatus(
            currentlyRunning = false,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(placed(1), open(), open()),
        )
        // "Teilweise gewertet" ist kein Zustand, sondern die Ablesung 0 < scored < total.
        assertEquals(MatchState.UPCOMING, status.state)
        assertEquals(3, status.teamsTotal)
        assertEquals(1, status.teamsScored)
    }

    @Test
    fun skippedWithoutRunOrFinish() {
        val status = MatchStatusLogic.matchStatus(
            currentlyRunning = false,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = true,
            teams = listOf(open(), open()),
        )
        assertEquals(MatchState.SKIPPED, status.state)
    }

    @Test
    fun withoutStartTimeUnscheduled() {
        val status = MatchStatusLogic.matchStatus(
            currentlyRunning = false,
            startTime = null,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(open()),
        )
        assertEquals(MatchState.UNSCHEDULED, status.state)
    }

    /** Ohne Mannschaften gibt es nichts zu werten - kein AWAITING_FINISH aus dem Nichts. */
    @Test
    fun matchWithoutTeamsIsNotAwaitingFinish() {
        val status = MatchStatusLogic.matchStatus(
            currentlyRunning = false,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = emptyList(),
        )
        assertEquals(MatchState.UPCOMING, status.state)
        assertEquals(0, status.teamsTotal)
        assertEquals(0, status.teamsScored)
    }

    /** null heißt "nicht erhoben" und ist etwas anderes als 0 ("erhoben, niemand draußen"). */
    @Test
    fun teamsOnWaterDefaultsToNotCollected() {
        val status = MatchStatusLogic.matchStatus(
            currentlyRunning = false,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(open()),
        )
        assertNull(status.teamsOnWater)

        val withWater = MatchStatusLogic.matchStatus(
            currentlyRunning = false,
            startTime = start,
            startedAt = null,
            finishedAt = null,
            skipped = false,
            teams = listOf(open()),
            teamsOnWater = 0,
        )
        assertEquals(0, withWater.teamsOnWater)
    }

    // --- roundCounters ---

    private fun status(state: MatchState) = MatchStatusDto(
        state = state,
        startedAt = null,
        teamsTotal = 2,
        teamsScored = 0,
    )

    @Test
    fun countersAreEmptyForAnEmptyRound() {
        val counters = MatchStatusLogic.roundCounters(emptyList())
        assertEquals(0, counters.total)
        assertEquals(0, counters.running)
        assertEquals(0, counters.open)
        assertEquals(0, counters.finished)
        assertEquals(0, counters.skipped)
    }

    @Test
    fun countersSortEveryStateIntoExactlyOneBucket() {
        val counters = MatchStatusLogic.roundCounters(
            listOf(
                status(MatchState.RUNNING),
                status(MatchState.FINISHED),
                status(MatchState.FINISHED),
                status(MatchState.FINISHED),
                status(MatchState.SKIPPED),
                status(MatchState.UPCOMING),
            )
        )
        assertEquals(6, counters.total)
        assertEquals(1, counters.running)
        assertEquals(1, counters.open)
        assertEquals(3, counters.finished)
        assertEquals(1, counters.skipped)
        assertEquals(
            counters.total,
            counters.running + counters.open + counters.finished + counters.skipped
        )
    }

    /** Ein Lauf, auf dessen Beenden alles wartet, ist offen - nicht beendet. */
    @Test
    fun awaitingFinishCountsAsOpen() {
        val counters = MatchStatusLogic.roundCounters(
            listOf(status(MatchState.AWAITING_FINISH), status(MatchState.UNSCHEDULED))
        )
        assertEquals(2, counters.open)
        assertEquals(0, counters.finished)
    }
}
