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
        assertEquals(false, needs.requirements)
    }

    // --- Programm-Reihenfolgen-Regel: Programmpunkte gelten als vorbei, sobald ein im
    // Programm späterer Lauf Aktivität zeigt (der Prod-Fall vom 11.08.2026: Besprechung
    // 15:00 nie „erledigt", Lauf 16:57 läuft — „Als Nächstes" darf nicht die Besprechung sein).

    private fun at(hour: Int, minute: Int = 0) = java.time.LocalDateTime.of(2026, 8, 11, hour, minute)

    private fun freeSlot(name: String, hour: Int) =
        match(name).copy(name = name, startTime = at(hour))

    @Test
    fun aDelayedFreeSlotIsPassedOnceALaterMatchHasStarted() {
        // Der 16:57-Lauf läuft: die 15-Uhr-Besprechung ist überholt, die 19-Uhr-Ehrung nicht.
        // Der Filter sitzt in EventInfoService.mergeWithPendingPlaceholders VOR dem Limit,
        // damit ein überholter Punkt den „Als Nächstes"-Block gar nicht erst besetzt.
        val upcoming = listOf(
            freeSlot("Besprechung", 15),
            match("U-nach-dem-Laufenden").copy(startTime = at(17, 9)),
            freeSlot("Siegerehrung", 19),
        )
        val cleaned = upcoming.filterNot {
            it.name != null && BoardLogic.freeSlotPassed(it.startTime, at(16, 57))
        }
        assertEquals(listOf("U-nach-dem-Laufenden", "Siegerehrung"), cleaned.map { it.competitionName })
        // „Als Nächstes" (+1) ist damit der Lauf nach dem laufenden, nicht die Besprechung.
        assertEquals(
            "U-nach-dem-Laufenden",
            BoardLogic.resolveOffset(1, running, cleaned, results).match?.competitionName,
        )
    }

    @Test
    fun withoutAnyProgressNothingIsPassed() {
        // Solange nichts gestartet ist, gibt es keine Schwelle — nichts gilt als überholt.
        assertEquals(false, BoardLogic.freeSlotPassed(at(15), null))
        // Gleichstand zählt als überholt: das Programm ist an diesem Punkt angekommen.
        assertEquals(true, BoardLogic.freeSlotPassed(at(15), at(15)))
        assertEquals(false, BoardLogic.freeSlotPassed(at(19), at(16, 57)))
    }

    @Test
    fun theProgramMarksPassedFreeSlotsAsFinished() {
        val program = listOf(
            BoardProgramEntry(startTime = at(14), competitionName = "früh", state = BoardProgramState.FINISHED),
            BoardProgramEntry(startTime = at(15), name = "Besprechung", state = BoardProgramState.UPCOMING),
            BoardProgramEntry(startTime = at(16, 57), competitionName = "läuft", state = BoardProgramState.RUNNING),
            BoardProgramEntry(startTime = at(19), name = "Siegerehrung", state = BoardProgramState.UPCOMING),
            // Ein verspäteter LAUF bleibt anstehend — nur Programmpunkte kippen.
            BoardProgramEntry(startTime = at(16), competitionName = "verspätet", state = BoardProgramState.UPCOMING),
        )
        val marked = BoardLogic.markPassedFreeSlots(program)
        assertEquals(BoardProgramState.FINISHED, marked[1].state)
        assertEquals(BoardProgramState.UPCOMING, marked[3].state)
        assertEquals(BoardProgramState.UPCOMING, marked[4].state)
    }

    // --- Verspätung: started_at − start_time des zuletzt gestarteten Laufs ---

    @Test
    fun delayComesFromTheLatestStartedMatch() {
        // Noch nichts gestartet: keine Aussage.
        assertNull(BoardLogic.currentDelaySeconds(emptyList()))
        // Verfrühung ist negativ.
        assertEquals(-300L, BoardLogic.currentDelaySeconds(listOf(at(9, 55) to at(10, 0))))
        // Mehrere Läufe: der zuletzt gestartete zählt, nicht der zuletzt geplante.
        assertEquals(
            18L * 60,
            BoardLogic.currentDelaySeconds(
                listOf(
                    at(10, 0) to at(10, 0),
                    at(11, 18) to at(11, 0),
                )
            ),
        )
        // Der zuletzt gestartete ohne geplante Zeit: nichts zu vergleichen.
        assertNull(BoardLogic.currentDelaySeconds(listOf(at(10, 0) to at(10, 0), at(11, 0) to null)))
    }

    // Die Sprecher-Kachel: Offset zählt in die Slot-Menge, und die Detailtiefe (Crew,
    // Weiterkommen, Bedingungen) ist ohne Schalter immer an.
    @Test
    fun dataNeedsOfMatchDetailForceFullDepth() {
        val config = BoardConfig(
            columns = 1,
            tiles = listOf(
                BoardTile(elements = listOf(BoardElement(type = BoardElementType.MATCH_DETAIL, offset = -1)))
            ),
        )
        val needs = BoardLogic.dataNeeds(config)
        assertEquals(setOf(-1), needs.offsets)
        assertEquals(true, needs.crewDetails)
        assertEquals(true, needs.advancement)
        assertEquals(true, needs.requirements)
    }
}
