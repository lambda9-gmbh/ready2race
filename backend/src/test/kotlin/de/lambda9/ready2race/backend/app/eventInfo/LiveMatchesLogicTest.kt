package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.eventInfo.boundary.LiveMatchesLogic
import de.lambda9.ready2race.backend.app.eventInfo.entity.LiveMatchInfo
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchState
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchStatusDto
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveMatchesLogicTest {

    private val noon: LocalDateTime = LocalDateTime.of(2026, 8, 14, 12, 0)

    private fun match(
        state: MatchState,
        startTime: LocalDateTime?,
        executionOrder: Int = 0,
        id: UUID = UUID.randomUUID(),
    ) = LiveMatchInfo(
        matchId = id,
        competitionId = UUID.randomUUID(),
        competitionName = "Männer Vierer",
        categoryName = null,
        roundName = "Vorlauf",
        matchName = "Lauf 1",
        startTime = startTime,
        status = MatchStatusDto(
            state = state,
            startedAt = if (state == MatchState.RUNNING) startTime else null,
            teamsTotal = 2,
            teamsScored = 0,
        ),
        executionOrder = executionOrder,
    )

    /** Wer die Seite öffnet, sucht zuerst, was gerade passiert. */
    @Test
    fun activatedMatchesComeFirst() {
        val running = match(MatchState.RUNNING, noon.plusHours(2))
        val upcoming = match(MatchState.UPCOMING, noon)

        val merged = LiveMatchesLogic.merge(listOf(running), listOf(upcoming), limit = 10)

        assertEquals(listOf(running.matchId, upcoming.matchId), merged.map { it.matchId })
    }

    @Test
    fun withinAGroupTheEarlierStartWins() {
        val late = match(MatchState.UPCOMING, noon.plusMinutes(30))
        val early = match(MatchState.UPCOMING, noon)

        val merged = LiveMatchesLogic.merge(emptyList(), listOf(late, early), limit = 10)

        assertEquals(listOf(early.matchId, late.matchId), merged.map { it.matchId })
    }

    /** Ein Lauf ohne Termin steht am Ende seiner Gruppe, nicht am Anfang. */
    @Test
    fun matchesWithoutAStartTimeGoLast() {
        val unscheduled = match(MatchState.UNSCHEDULED, null)
        val scheduled = match(MatchState.UPCOMING, noon.plusHours(3))

        val merged = LiveMatchesLogic.merge(emptyList(), listOf(unscheduled, scheduled), limit = 10)

        assertEquals(listOf(scheduled.matchId, unscheduled.matchId), merged.map { it.matchId })
    }

    @Test
    fun sameStartTimeIsOrderedByExecutionOrder() {
        val second = match(MatchState.UPCOMING, noon, executionOrder = 2)
        val first = match(MatchState.UPCOMING, noon, executionOrder = 1)

        val merged = LiveMatchesLogic.merge(emptyList(), listOf(second, first), limit = 10)

        assertEquals(listOf(first.matchId, second.matchId), merged.map { it.matchId })
    }

    /**
     * Die zwei Abfragen laufen nacheinander. Wird ein Lauf dazwischen aktiviert, steht er in beiden
     * Listen - der aktivierte Eintrag trägt die frischere Aussage.
     */
    @Test
    fun aMatchInBothListsAppearsOnceAndActivatedWins() {
        val id = UUID.randomUUID()
        val activated = match(MatchState.PREPARING, noon, id = id)
        val stale = match(MatchState.UPCOMING, noon, id = id)

        val merged = LiveMatchesLogic.merge(listOf(activated), listOf(stale), limit = 10)

        assertEquals(1, merged.size)
        assertEquals(MatchState.PREPARING, merged.single().status.state)
    }

    /** Der Deckel gilt über beide Zweige - sonst verdrängen anstehende Läufe den laufenden. */
    @Test
    fun theLimitAppliesToBothBranchesTogether() {
        val running = match(MatchState.RUNNING, noon)
        val upcoming = (1..5).map { match(MatchState.UPCOMING, noon.plusMinutes(it * 10L)) }

        val merged = LiveMatchesLogic.merge(listOf(running), upcoming, limit = 3)

        assertEquals(3, merged.size)
        assertEquals(running.matchId, merged.first().matchId)
    }

    @Test
    fun aLimitOfZeroOrLessYieldsNothing() {
        val running = match(MatchState.RUNNING, noon)

        assertTrue(LiveMatchesLogic.merge(listOf(running), emptyList(), limit = 0).isEmpty())
        assertTrue(LiveMatchesLogic.merge(listOf(running), emptyList(), limit = -1).isEmpty())
    }

    /**
     * Der Schutz der Ergebnisfreigabe. Ein beendeter oder vollständig gewerteter Lauf gehört
     * ausschließlich zu `/latest-match-results`, wo `PublicResultsVisibility` entscheidet, ob er
     * gezeigt werden darf. Die beiden Abfragen hinter [LiveMatchesLogic.merge] können ihn per SQL
     * gar nicht erst liefern (`CompetitionMatchRepo.getUpcomingMatchesForBoard` schließt
     * `finished_at is not null` und „alle Boote gewertet" aus) - kommt er trotzdem an, ist das ein
     * Fehler, und die Zusammenführung lässt ihn nicht durch.
     */
    @Test
    fun finishedAndAwaitingFinishMatchesNeverReachTheLiveList() {
        val finished = match(MatchState.FINISHED, noon)
        val awaiting = match(MatchState.AWAITING_FINISH, noon)
        val upcoming = match(MatchState.UPCOMING, noon.plusMinutes(20))

        val merged = LiveMatchesLogic.merge(
            activated = listOf(finished),
            upcoming = listOf(awaiting, upcoming),
            limit = 10,
        )

        assertEquals(listOf(upcoming.matchId), merged.map { it.matchId })
    }

    /** Abgesagte Läufe bleiben stehen - sie sind die Antwort auf „wo ist mein Lauf?". */
    @Test
    fun cancelledMatchesStayInTheList() {
        val cancelled = match(MatchState.SKIPPED, noon).copy(cancelled = true)

        val merged = LiveMatchesLogic.merge(emptyList(), listOf(cancelled), limit = 10)

        assertEquals(listOf(cancelled.matchId), merged.map { it.matchId })
    }
}
