package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.event.entity.PublicResultsVisibility
import de.lambda9.ready2race.backend.app.eventInfo.boundary.MyEventLogic
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardStartState
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Mein Event" darf ein Ergebnis nicht früher zeigen als die Athleten-Anzeige. Diese Tests
 * halten fest, dass die Aufteilung dieselben Regeln benutzt wie AthleteBoardLogic — driften
 * die beiden auseinander, steht dasselbe Rennen auf zwei Bildschirmen unterschiedlich da.
 */
class MyEventLogicTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

    private fun raw(
        startTime: LocalDateTime? = null,
        actualStartTime: LocalDateTime? = null,
        finishedAt: LocalDateTime? = null,
        allTeamsScored: Boolean = false,
        currentlyRunning: Boolean = false,
        deregistered: Boolean = false,
    ) = MyEventLogic.RawMatch(
        matchId = UUID.randomUUID(),
        competitionName = "Wettkampf",
        categoryName = null,
        roundName = null,
        matchName = null,
        startTime = startTime,
        actualStartTime = actualStartTime,
        finishedAt = finishedAt,
        allTeamsScored = allTeamsScored,
        currentlyRunning = currentlyRunning,
        lane = 1,
        teamName = null,
        clubName = "Verein",
        teamMembers = emptyList(),
        place = null,
        timeString = null,
        penaltySeconds = null,
        penaltyNote = null,
        failed = false,
        failedReason = null,
        deregistered = deregistered,
        deregisteredReason = null,
    )

    @Test
    fun finishedMatchBecomesResult() {
        val split = MyEventLogic.split(
            entries = listOf(raw(startTime = now.minusHours(1), finishedAt = now.minusMinutes(30))),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertEquals(1, split.results.size)
        assertTrue(split.running.isEmpty())
        assertTrue(split.upcoming.isEmpty())
    }

    @Test
    fun scoredButUnfinishedMatchStaysHiddenUnderFinishedOnly() {
        val split = MyEventLogic.split(
            entries = listOf(raw(startTime = now.minusHours(1), allTeamsScored = true, currentlyRunning = true)),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertTrue(split.results.isEmpty())
        assertEquals(1, split.running.size)
    }

    @Test
    fun scoredButUnfinishedMatchAppearsUnderResultsComplete() {
        val split = MyEventLogic.split(
            entries = listOf(raw(startTime = now.minusHours(1), allTeamsScored = true, currentlyRunning = true)),
            now = now,
            visibility = PublicResultsVisibility.RESULTS_COMPLETE,
            showCountdown = true,
        )
        assertEquals(1, split.results.size)
        assertTrue(split.running.isEmpty())
    }

    @Test
    fun upcomingMatchesAreSortedByStartTime() {
        val late = raw(startTime = now.plusHours(2))
        val early = raw(startTime = now.plusMinutes(30))
        val split = MyEventLogic.split(
            entries = listOf(late, early),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertEquals(listOf(early.matchId, late.matchId), split.upcoming.map { it.matchId })
    }

    @Test
    fun passedStartTimeYieldsOverdueInsteadOfNegativeCountdown() {
        val split = MyEventLogic.split(
            entries = listOf(raw(startTime = now.minusMinutes(5))),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertEquals(AthleteBoardStartState.OVERDUE, split.upcoming.single().startState)
    }

    @Test
    fun resultsAreSortedNewestFirst() {
        val older = raw(startTime = now.minusHours(3), finishedAt = now.minusHours(3))
        val newer = raw(startTime = now.minusHours(1), finishedAt = now.minusHours(1))
        val split = MyEventLogic.split(
            entries = listOf(older, newer),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertEquals(listOf(newer.matchId, older.matchId), split.results.map { it.matchId })
    }

    @Test
    fun timelessResultStaysBehindTimedResult() {
        // Vor dem Start abgemeldet: kein startTime, kein actualStartTime, aber via finishedAt
        // trotzdem ein Ergebnis. Das darf beim "neuestes zuerst" nicht vor einem Ergebnis mit
        // echter Zeitangabe stehen.
        val timeless = raw(finishedAt = now.minusMinutes(10), deregistered = true)
        val timed = raw(startTime = now.minusHours(1), finishedAt = now.minusHours(1))
        val split = MyEventLogic.split(
            entries = listOf(timeless, timed),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertEquals(listOf(timed.matchId, timeless.matchId), split.results.map { it.matchId })
    }
}
