package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.eventInfo.boundary.BoardLogic
import de.lambda9.ready2race.backend.app.eventInfo.entity.*
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchState
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Die Offset-Auflösung ist das Herz des Board-Systems: eine Verschiebung um eins zeigt
 * auf einem montierten Bildschirm den falschen Lauf, ohne dass es jemandem auffällt.
 */
class BoardLogicTest {

    private fun match(name: String) = AthleteBoardMatch(
        matchId = UUID.randomUUID(), competitionName = name, categoryName = null,
        roundName = null, matchName = null, startTime = null,
        state = MatchState.RUNNING, startState = AthleteBoardStartState.UNSCHEDULED,
        teams = emptyList(),
    )

    private fun result(name: String) = AthleteBoardResult(
        matchId = UUID.randomUUID(), competitionName = name, categoryName = null,
        roundName = null, matchName = null, startTime = null, actualStartTime = null,
        teams = emptyList(),
    )

    // running aufsteigend nach tatsächlichem Start, results neuestes zuerst, upcoming aufsteigend.
    private val running = listOf(match("R-früh"), match("R-spät"))
    private val upcoming = listOf(match("U1"), match("U2"))
    private val results = listOf(result("E-neu"), result("E-alt"))

    @Test
    fun zeroIsTheLastStartedRunningMatch() {
        assertEquals("R-spät", BoardLogic.resolveOffset(0, running, upcoming, results).match?.competitionName)
    }

    @Test
    fun negativeOffsetsWalkThroughEarlierRunningThenResults() {
        // -1 ist der parallel laufende frühere Lauf, erst -2 erreicht die Ergebnisse.
        assertEquals("R-früh", BoardLogic.resolveOffset(-1, running, upcoming, results).match?.competitionName)
        assertEquals("E-neu", BoardLogic.resolveOffset(-2, running, upcoming, results).result?.competitionName)
        assertEquals("E-alt", BoardLogic.resolveOffset(-3, running, upcoming, results).result?.competitionName)
    }

    @Test
    fun withoutRunningZeroIsEmptyAndNeighboursExist() {
        val slot0 = BoardLogic.resolveOffset(0, emptyList(), upcoming, results)
        assertNull(slot0.match)
        assertNull(slot0.result)
        assertEquals("E-neu", BoardLogic.resolveOffset(-1, emptyList(), upcoming, results).result?.competitionName)
        assertEquals("U1", BoardLogic.resolveOffset(1, emptyList(), upcoming, results).match?.competitionName)
    }

    @Test
    fun positiveOffsetsIndexUpcoming() {
        assertEquals("U2", BoardLogic.resolveOffset(2, running, upcoming, results).match?.competitionName)
        val beyond = BoardLogic.resolveOffset(3, running, upcoming, results)
        assertNull(beyond.match)
        assertNull(beyond.result)
    }

    @Test
    fun dataNeedsCoverOffsetsAndLists() {
        val config = BoardConfig(
            columns = 2,
            tiles = listOf(
                BoardTile(
                    elements = listOf(
                        BoardElement(type = BoardElementType.MATCH, offset = -4),
                        BoardElement(type = BoardElementType.MATCH, offset = 2),
                    )
                ),
                BoardTile(
                    elements = listOf(
                        BoardElement(type = BoardElementType.MATCH_LIST, listMode = BoardListMode.UPCOMING, limit = 8),
                        BoardElement(type = BoardElementType.MATCH_LIST, listMode = BoardListMode.UPCOMING, limit = 3),
                        BoardElement(type = BoardElementType.TEXT, text = "Hi"),
                    )
                ),
            ),
        )
        val needs = BoardLogic.dataNeeds(config)
        assertEquals(setOf(-4, 2), needs.offsets)
        // Negative Offsets können in parallel laufende Läufe zeigen: |min|+1 laufende abrufen.
        assertEquals(5, needs.runningLimit)
        assertEquals(8, needs.upcomingLimit) // max(Offset +2, Liste 8)
        assertEquals(4, needs.resultsLimit) // |−4|
        assertEquals(mapOf(BoardListMode.UPCOMING to 8), needs.listLimits)
    }

    @Test
    fun dataNeedsCarryAnnouncerScheduleAndCeremonies() {
        val competitionId = UUID.randomUUID()
        val config = BoardConfig(
            columns = 2,
            tiles = listOf(
                BoardTile(
                    elements = listOf(
                        BoardElement(type = BoardElementType.MATCH, offset = 0, showCrewDetails = true, showAdvancement = true),
                        BoardElement(type = BoardElementType.MATCH_LIST, listMode = BoardListMode.SCHEDULE, limit = 10),
                        // Dieselbe Ehrung zweimal konfiguriert wird nur einmal gerechnet.
                        BoardElement(type = BoardElementType.AWARD_CEREMONY, competitionId = competitionId),
                        BoardElement(type = BoardElementType.AWARD_CEREMONY, competitionId = competitionId),
                    )
                ),
            ),
        )
        val needs = BoardLogic.dataNeeds(config)
        assertEquals(true, needs.crewDetails)
        assertEquals(true, needs.advancement)
        assertEquals(true, needs.schedule)
        assertEquals(1, needs.ceremonies.size)
        assertEquals(competitionId, needs.ceremonies.single().competitionId)
    }

    @Test
    fun dataNeedsWithoutMatchElementsStayMinimal() {
        val config = BoardConfig(
            columns = 1,
            tiles = listOf(BoardTile(elements = listOf(BoardElement(type = BoardElementType.CLOCK)))),
        )
        val needs = BoardLogic.dataNeeds(config)
        assertEquals(emptySet<Int>(), needs.offsets)
        assertEquals(1, needs.runningLimit)
        assertEquals(1, needs.upcomingLimit)
        assertEquals(1, needs.resultsLimit)
    }
}
