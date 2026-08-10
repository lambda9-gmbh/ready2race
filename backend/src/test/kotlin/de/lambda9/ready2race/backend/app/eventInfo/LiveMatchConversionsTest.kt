package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.eventInfo.control.toLiveMatchInfo
import de.lambda9.ready2race.backend.app.eventInfo.control.toRunningMatchTeamInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.RunningMatchInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.RunningMatchTeamInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.UpcomingCompetitionMatchInfo
import de.lambda9.ready2race.backend.app.eventInfo.entity.UpcomingMatchTeamInfo
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchState
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Der tatsächliche Schutz der Ergebnisfreigabe im Tab „Live": nicht der Filter in
 * [de.lambda9.ready2race.backend.app.eventInfo.boundary.LiveMatchesLogic.merge] (der ist nur eine
 * Zusicherung über den Inhalt der fertigen Liste, siehe dortiges KDoc), sondern diese beiden
 * Umwandlungen. Reine Logik, ohne Datenbank - genau wie `LiveMatchesLogicTest`, dessen Stil diese
 * Datei übernimmt.
 */
class LiveMatchConversionsTest {

    private val start = LocalDateTime.of(2026, 8, 14, 12, 0)

    // --- UpcomingMatchTeamInfo.toRunningMatchTeamInfo ---

    private fun upcomingTeam(teamId: UUID = UUID.randomUUID()) = UpcomingMatchTeamInfo(
        teamId = teamId,
        teamName = "Renn-Achter",
        teamNumber = 2,
        startNumber = 4,
        clubName = "RC Förde",
        clubsShort = "RCF",
        clubsFull = "Ruder-Club Förde",
        participants = emptyList(),
    )

    /**
     * Die Quellabfrage (`CompetitionMatchTeamRepo.getTeamsForUpcomingMatch`) fragt Platz und Zeit
     * gar nicht erst ab - die Umwandlung liefert deshalb für JEDES Ergebnisfeld den leeren Wert,
     * unabhängig davon, was sonst im Boot steht. Genau daran hängt, dass der anstehende Zweig
     * keine Ergebnisse veröffentlichen kann.
     */
    @Test
    fun upcomingTeamHasNoResultFields() {
        val result = upcomingTeam().toRunningMatchTeamInfo()

        assertNull(result.currentScore)
        assertNull(result.currentPosition)
        assertNull(result.timeString)
        assertNull(result.penaltySeconds)
        assertNull(result.penaltyNote)
        assertNull(result.failedReason)
        assertFalse(result.failed)
    }

    /** Alles, was KEIN Ergebnisfeld ist, reicht die Umwandlung unverändert durch. */
    @Test
    fun upcomingTeamPassesThroughEverythingElse() {
        val id = UUID.randomUUID()
        val team = upcomingTeam(id)

        val result = team.toRunningMatchTeamInfo()

        assertEquals(id, result.teamId)
        assertEquals(team.teamName, result.teamName)
        assertEquals(team.teamNumber, result.teamNumber)
        assertEquals(team.startNumber, result.startNumber)
        assertEquals(team.clubName, result.clubName)
        assertEquals(team.clubsShort, result.clubsShort)
        assertEquals(team.clubsFull, result.clubsFull)
        assertEquals(team.participants, result.participants)
    }

    // --- UpcomingCompetitionMatchInfo.toLiveMatchInfo ---

    private fun upcomingMatch(
        startTime: LocalDateTime? = start,
        cancelled: Boolean = false,
        teams: List<UpcomingMatchTeamInfo> = emptyList(),
    ) = UpcomingCompetitionMatchInfo(
        matchId = UUID.randomUUID(),
        matchNumber = null,
        competitionId = UUID.randomUUID(),
        competitionName = "Männer Vierer",
        categoryName = null,
        scheduledStartTime = startTime,
        placeName = null,
        roundNumber = null,
        roundName = "Vorlauf",
        matchName = "Lauf 1",
        executionOrder = 0,
        teams = teams,
        cancelled = cancelled,
    )

    @Test
    fun ordinaryUpcomingMatchIsUpcoming() {
        val result = upcomingMatch().toLiveMatchInfo()
        assertEquals(MatchState.UPCOMING, result.status.state)
    }

    @Test
    fun cancelledUpcomingMatchIsSkipped() {
        val result = upcomingMatch(cancelled = true).toLiveMatchInfo()
        assertEquals(MatchState.SKIPPED, result.status.state)
    }

    @Test
    fun upcomingMatchWithoutAStartTimeIsUnscheduled() {
        val result = upcomingMatch(startTime = null).toLiveMatchInfo()
        assertEquals(MatchState.UNSCHEDULED, result.status.state)
    }

    /**
     * Der eigentliche Beleg für den Schutz: selbst ein anstehender Lauf MIT Mannschaften kommt nie
     * als FINISHED oder AWAITING_FINISH an, weil jedes Boot als ungewertet übergeben wird
     * (`MatchStatusTeam(place = null, failed = false, deregistered = false)`) - unabhängig davon,
     * was die Quellabfrage tatsächlich mitbrächte.
     */
    @Test
    fun upcomingMatchNeverReachesFinishedOrAwaitingFinishEvenWithTeams() {
        val result = upcomingMatch(teams = listOf(upcomingTeam(), upcomingTeam())).toLiveMatchInfo()

        assertEquals(MatchState.UPCOMING, result.status.state)
        assertNotEquals(MatchState.FINISHED, result.status.state)
        assertNotEquals(MatchState.AWAITING_FINISH, result.status.state)
    }

    // --- RunningMatchInfo.toLiveMatchInfo ---

    private fun runningTeam() = RunningMatchTeamInfo(
        teamId = UUID.randomUUID(),
        teamName = "Renn-Achter",
        teamNumber = 1,
        startNumber = 3,
        clubName = "RC Förde",
        clubsShort = "RCF",
        clubsFull = "Ruder-Club Förde",
        currentScore = null,
        currentPosition = 2,
        timeString = "6:12,4",
        penaltySeconds = 5,
        penaltyNote = "Wende geschnitten",
        failed = false,
        failedReason = null,
        participants = emptyList(),
    )

    private fun runningMatch(
        activatedAt: LocalDateTime? = start.minusMinutes(3),
        startedAt: LocalDateTime? = null,
        teams: List<RunningMatchTeamInfo> = emptyList(),
    ) = RunningMatchInfo(
        matchId = UUID.randomUUID(),
        matchNumber = null,
        competitionId = UUID.randomUUID(),
        competitionName = "Männer Vierer",
        categoryName = null,
        startTime = start,
        activatedAt = activatedAt,
        startedAt = startedAt,
        elapsedMinutes = null,
        placeName = null,
        roundNumber = null,
        roundName = "Vorlauf",
        matchName = "Lauf 1",
        executionOrder = 0,
        teams = teams,
    )

    @Test
    fun activatedWithoutARealStartIsPreparing() {
        val result = runningMatch(startedAt = null).toLiveMatchInfo()
        assertEquals(MatchState.PREPARING, result.status.state)
    }

    @Test
    fun activatedWithARealStartIsRunning() {
        val result = runningMatch(startedAt = start.plusMinutes(1)).toLiveMatchInfo()
        assertEquals(MatchState.RUNNING, result.status.state)
    }

    /**
     * Anders als beim anstehenden Zweig: die Teilergebnisse eines laufenden Laufs gehen
     * unverändert durch - genau wie beim vorbestehenden Endpoint `/running-matches`, den dieser
     * Tab schon vor der Live-Liste abrief. Das ist gewollt (siehe KDoc über
     * `toRunningMatchTeamInfo`), keine Aufweichung.
     */
    @Test
    fun runningMatchTeamsKeepTheirPartialResults() {
        val team = runningTeam()
        val result = runningMatch(startedAt = start.plusMinutes(1), teams = listOf(team)).toLiveMatchInfo()

        assertEquals(listOf(team), result.teams)
    }
}
