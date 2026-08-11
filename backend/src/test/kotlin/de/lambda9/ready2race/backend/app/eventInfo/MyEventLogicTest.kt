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
    fun overdueMatchBeyondGraceMovesToTheEndOfUpcoming() {
        // Ein Lauf, der gefahren, aber nie „beendet" wurde und `currently_running` verloren hat,
        // bleibt sonst den ganzen Tag anstehend — und stünde aufsteigend sortiert vor dem
        // tatsächlich nächsten Lauf. Dann zeigte die Karte oben um 13:00 „Dein nächster Lauf,
        // 09:00", während der echte 14-Uhr-Lauf darunter verschwindet. Die Nachfrist ist
        // dieselbe wie auf der Athleten-Anzeige.
        val forgotten = raw(startTime = now.minusHours(3))
        val next = raw(startTime = now.plusHours(4))
        val split = MyEventLogic.split(
            entries = listOf(forgotten, next),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertEquals(listOf(next.matchId, forgotten.matchId), split.upcoming.map { it.matchId })
    }

    @Test
    fun overdueMatchWithinGraceKeepsItsPlace() {
        // Innerhalb der Nachfrist ist eine verstrichene Startzeit der Normalfall (der Start
        // verzögert sich) — der Lauf bleibt vorn und wird nur als überfällig gezeichnet.
        val delayed = raw(startTime = now.minusMinutes(10))
        val later = raw(startTime = now.plusHours(2))
        val split = MyEventLogic.split(
            entries = listOf(later, delayed),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertEquals(listOf(delayed.matchId, later.matchId), split.upcoming.map { it.matchId })
    }

    @Test
    fun overdueMatchBeyondGraceStaysVisible() {
        // Verworfen wird er nicht: auf der Wandanzeige geht es um den Betrieb, hier um den
        // eigenen Tag — der eigene Lauf darf nicht kommentarlos verschwinden.
        val forgotten = raw(startTime = now.minusHours(3))
        val split = MyEventLogic.split(
            entries = listOf(forgotten),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertEquals(listOf(forgotten.matchId), split.upcoming.map { it.matchId })
    }

    @Test
    fun unscheduledMatchStaysAheadOfMatchesBeyondGrace() {
        val forgotten = raw(startTime = now.minusHours(3))
        val unscheduled = raw(startTime = null)
        val split = MyEventLogic.split(
            entries = listOf(forgotten, unscheduled),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        assertEquals(listOf(unscheduled.matchId, forgotten.matchId), split.upcoming.map { it.matchId })
    }

    @Test
    fun deregisteredMatchIsMarkedAndGetsNoCountdown() {
        // Solange der Lauf kein öffentliches Ergebnis ist, steht die Abmeldung nur am kommenden
        // Lauf. Ohne Kennzeichen sähe er wie ein ganz normaler Start aus.
        val split = MyEventLogic.split(
            entries = listOf(raw(startTime = now.plusHours(1), deregistered = true)),
            now = now,
            visibility = PublicResultsVisibility.FINISHED_ONLY,
            showCountdown = true,
        )
        val match = split.upcoming.single()
        assertTrue(match.deregistered)
        assertEquals(AthleteBoardStartState.SCHEDULED, match.startState)
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

    // Erledigungsfenster der Bedingungen: Bezugsgröße ist der erste KÜNFTIGE Start. Die
    // Kandidatenliste kommt aus split.upcoming und kann deshalb auch überfällige und
    // abgemeldete Läufe enthalten — beide dürfen das Fenster nicht setzen.

    private fun upcoming(entries: List<MyEventLogic.RawMatch>) = MyEventLogic.split(
        entries = entries,
        now = now,
        visibility = PublicResultsVisibility.FINISHED_ONLY,
        showCountdown = true,
    ).upcoming

    @Test
    fun firstFutureStartIsTheEarliestFutureOne() {
        val matches = upcoming(
            listOf(
                raw(startTime = now.plusHours(3)),
                raw(startTime = now.plusMinutes(45)),
            )
        )
        assertEquals(now.plusMinutes(45), MyEventLogic.firstFutureStart(matches, now))
    }

    @Test
    fun overdueAndDeregisteredStartsDoNotCount() {
        // Der überfällige 09-Uhr-Lauf steht noch in upcoming, liegt aber in der Vergangenheit;
        // der abgemeldete findet für diese Person nicht statt.
        val matches = upcoming(
            listOf(
                raw(startTime = now.minusHours(1)),
                raw(startTime = now.plusMinutes(30), deregistered = true),
                raw(startTime = now.plusHours(2)),
            )
        )
        assertEquals(now.plusHours(2), MyEventLogic.firstFutureStart(matches, now))
    }

    @Test
    fun noFutureStartMeansNoWindow() {
        val matches = upcoming(listOf(raw(startTime = now.minusHours(1))))
        assertEquals(null, MyEventLogic.firstFutureStart(matches, now))
        assertEquals(null, MyEventLogic.checkWindowBound(null, 120))
    }

    @Test
    fun windowBoundIsMinutesBeforeTheStart() {
        val start = now.plusHours(2)
        assertEquals(start.minusMinutes(120), MyEventLogic.checkWindowBound(start, 120))
        // Ohne Minutenangabe gibt es keine Grenze — ein erfundenes Fenster wäre schlimmer
        // als keins.
        assertEquals(null, MyEventLogic.checkWindowBound(start, null))
    }
}
