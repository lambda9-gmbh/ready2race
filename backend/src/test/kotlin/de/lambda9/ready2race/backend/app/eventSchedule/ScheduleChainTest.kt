package de.lambda9.ready2race.backend.app.eventSchedule

import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ChainDecision
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ChainSlot
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ScheduleChain
import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState.*
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScheduleChainTest {

    private val base = LocalDateTime.of(2026, 8, 17, 10, 0)
    private fun slot(
        min: Long,
        state: de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState,
        matchId: UUID? = null,
        finished: Boolean = false,
        open: Boolean = true,
        activatedAt: LocalDateTime? = null,
        startedAt: LocalDateTime? = null,
    ) = ChainSlot(
        UUID.randomUUID(), base.plusMinutes(min), state, matchId, finished, open, activatedAt, startedAt,
    )

    @Test
    fun activatesTheNextLinkedOpenMatch() {
        val m = UUID.randomUUID()
        val decision = ScheduleChain.decideNext(listOf(slot(10, LINKED, m)))
        assertEquals(ChainDecision.Activate(listOf(m)), decision)
    }

    @Test
    fun waitsAtAWaitingSlotInsteadOfSkippingAhead() {
        val m = UUID.randomUUID()
        val decision = ScheduleChain.decideNext(
            listOf(slot(10, WAITING), slot(20, LINKED, m)),
        )
        assertIs<ChainDecision.WaitingForRound>(decision)
    }

    @Test
    fun skippedObsoleteAndFreeSlotsArePassedOver() {
        val m = UUID.randomUUID()
        val decision = ScheduleChain.decideNext(
            listOf(slot(10, SKIPPED), slot(20, OBSOLETE), slot(30, FREE), slot(40, LINKED, m)),
        )
        assertEquals(ChainDecision.Activate(listOf(m)), decision)
    }

    @Test
    fun finishedOrClosedMatchesArePassedOver() {
        val m = UUID.randomUUID()
        val decision = ScheduleChain.decideNext(
            listOf(
                slot(10, LINKED, UUID.randomUUID(), finished = true),
                slot(20, LINKED, UUID.randomUUID(), open = false),
                slot(30, LINKED, m),
            ),
        )
        assertEquals(ChainDecision.Activate(listOf(m)), decision)
    }

    @Test
    fun parallelStartsActivateTogether() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val s1 = slot(10, LINKED, a)
        val s2 = ChainSlot(UUID.randomUUID(), s1.startTime, LINKED, b, matchFinished = false, matchOpen = true)
        assertEquals(ChainDecision.Activate(listOf(a, b)), ScheduleChain.decideNext(listOf(s1, s2)))
    }

    @Test
    fun emptyTailMeansNothingToDo() {
        assertIs<ChainDecision.NothingToDo>(ScheduleChain.decideNext(emptyList()))
    }

    @Test
    fun mixedGroupWithWaitingSlotWaitsAsAUnit() {
        val m = UUID.randomUUID()
        val t = base.plusMinutes(10)
        val linked = ChainSlot(UUID.randomUUID(), t, LINKED, m, matchFinished = false, matchOpen = true)
        val waiting = ChainSlot(UUID.randomUUID(), t, WAITING, null, matchFinished = false, matchOpen = false)
        assertIs<ChainDecision.WaitingForRound>(ScheduleChain.decideNext(listOf(linked, waiting)))
    }

    @Test
    fun groupWithOnlyFinishedMatchesIsPassedOver() {
        val m = UUID.randomUUID()
        val t = base.plusMinutes(10)
        val done = ChainSlot(UUID.randomUUID(), t, LINKED, UUID.randomUUID(), matchFinished = true, matchOpen = false)
        val next = ChainSlot(UUID.randomUUID(), base.plusMinutes(20), LINKED, m, matchFinished = false, matchOpen = true)
        assertEquals(ChainDecision.Activate(listOf(m)), ScheduleChain.decideNext(listOf(done, next)))
    }

    // --- parallele Startgruppen müssen als Ganzes fertig sein (Thomas' Vorgabe) ---
    //
    // getChainSlots liefert seit dem Fix (>= statt > auf start_time) die eigene Gruppe des gerade
    // beendeten Laufs mit in den Walk - decideNext muss deshalb einen noch laufenden Sibling-Lauf
    // derselben Startzeit erkennen und darf dann NICHT zur nächsten Gruppe vorrücken.

    @Test
    fun blocksAdvanceWhileASiblingOfTheSameStartIsStillRunning() {
        val t = base.plusMinutes(10)
        val finished = ChainSlot(UUID.randomUUID(), t, LINKED, UUID.randomUUID(), matchFinished = true, matchOpen = false)
        val stillRunning = ChainSlot(
            UUID.randomUUID(), t, LINKED, UUID.randomUUID(),
            matchFinished = false, matchOpen = true,
            matchActivatedAt = t.minusMinutes(5), matchStartedAt = t,
        )
        val next = ChainSlot(
            UUID.randomUUID(), base.plusMinutes(20), LINKED, UUID.randomUUID(),
            matchFinished = false, matchOpen = true,
        )

        assertIs<ChainDecision.NothingToDo>(ScheduleChain.decideNext(listOf(finished, stillRunning, next)))
    }

    @Test
    fun advancesOnceTheLastSiblingOfTheStartGroupFinishes() {
        val t = base.plusMinutes(10)
        val finishedA = ChainSlot(UUID.randomUUID(), t, LINKED, UUID.randomUUID(), matchFinished = true, matchOpen = false)
        val finishedB = ChainSlot(UUID.randomUUID(), t, LINKED, UUID.randomUUID(), matchFinished = true, matchOpen = false)
        val next = UUID.randomUUID()
        val nextSlot = ChainSlot(
            UUID.randomUUID(), base.plusMinutes(20), LINKED, next,
            matchFinished = false, matchOpen = true,
        )

        assertEquals(
            ChainDecision.Activate(listOf(next)),
            ScheduleChain.decideNext(listOf(finishedA, finishedB, nextSlot)),
        )
    }

    // --- die Kette hängt an finished_at, nicht am Anzeigezustand ---

    @Test
    fun aFullyScoredButUnfinishedMatchDoesNotAdvanceTheChain() {
        // Seit dem 06.08.2026 heißt "alle Boote gewertet" im Dashboard AWAITING_FINISH statt
        // FINISHED. Die Kette darf davon nichts merken: ChainSlot kennt nur matchFinished
        // (= competition_match.finished_at) und keine Ergebnis-Vollständigkeit. Ein solcher Lauf
        // ist weiterhin unbeendet und offen - die Gruppe blockiert, die nächste Startzeit bleibt
        // stehen, bis jemand beendet.
        val t = base.plusMinutes(10)
        val scoredButNotFinished = ChainSlot(
            UUID.randomUUID(), t, LINKED, UUID.randomUUID(),
            matchFinished = false, matchOpen = true,
            matchActivatedAt = t.minusMinutes(5), matchStartedAt = t,
        )
        val next = ChainSlot(
            UUID.randomUUID(), base.plusMinutes(20), LINKED, UUID.randomUUID(),
            matchFinished = false, matchOpen = true,
        )

        assertIs<ChainDecision.NothingToDo>(
            ScheduleChain.decideNext(listOf(scoredButNotFinished, next)),
        )
    }

    @Test
    fun theSameMatchAdvancesTheChainOnceItIsFinished() {
        // Gegenprobe zum Test darüber: erst der Beenden-Klick (finished_at) rückt die Kette vor.
        val t = base.plusMinutes(10)
        val finished = ChainSlot(
            UUID.randomUUID(), t, LINKED, UUID.randomUUID(),
            matchFinished = true, matchOpen = false,
            matchActivatedAt = t.minusMinutes(5), matchStartedAt = t,
        )
        val m = UUID.randomUUID()
        val next = ChainSlot(
            UUID.randomUUID(), base.plusMinutes(20), LINKED, m,
            matchFinished = false, matchOpen = true,
        )

        assertEquals(ChainDecision.Activate(listOf(m)), ScheduleChain.decideNext(listOf(finished, next)))
    }

    // --- nur der Ist-Start blockiert, nicht schon die Aktivierung ---
    //
    // Seit der Trennung von Aktivierung und Ist-Start (Entwurf 09.08.2026) heißt "läuft noch"
    // matchStartedAt != null. Ein Lauf, den die Kette an den Start gerufen hat, dessen Boote aber
    // noch am Steg liegen, hält die nächste Startgruppe nicht auf.

    @Test
    fun anActivatedButUnstartedSiblingDoesNotBlockTheGroup() {
        // Der einzige Slot ist bereits aktiviert und damit nicht mehr aktivierbar - NothingToDo ist
        // hier richtig. Geprüft wird, dass die Entscheidung NICHT schon an siblingStillRunning
        // hängen bleibt, sondern die Suche in der nächsten Gruppe fortsetzen würde.
        val decision = ScheduleChain.decideNext(
            listOf(slot(10, LINKED, UUID.randomUUID(), activatedAt = base.plusMinutes(5))),
        )
        assertIs<ChainDecision.NothingToDo>(decision)
    }

    @Test
    fun aTrulyStartedSiblingBlocksTheGroup() {
        val t = base.plusMinutes(10)
        val running = ChainSlot(
            UUID.randomUUID(), t, LINKED, UUID.randomUUID(),
            matchFinished = false, matchOpen = true,
            matchActivatedAt = t.minusMinutes(5), matchStartedAt = t.plusMinutes(1),
        )
        val activatable = ChainSlot(
            UUID.randomUUID(), t, LINKED, UUID.randomUUID(),
            matchFinished = false, matchOpen = true,
        )

        assertIs<ChainDecision.NothingToDo>(ScheduleChain.decideNext(listOf(running, activatable)))
    }

    @Test
    fun theChainAdvancesWhenTheGroupHoldsOnlyPreparedMatches() {
        val prepared = slot(10, LINKED, UUID.randomUUID(), activatedAt = base.plusMinutes(5))
        val m = UUID.randomUUID()
        val next = slot(20, LINKED, m)

        assertEquals(ChainDecision.Activate(listOf(m)), ScheduleChain.decideNext(listOf(prepared, next)))
    }

    @Test
    fun aSoloFinishWithNoRunningSiblingStillAdvances() {
        // Kein paralleler Lauf in der Gruppe (nur der gerade beendete selbst) - unverändertes
        // Verhalten, die Kette darf normal weiterlaufen.
        val t = base.plusMinutes(10)
        val finished = ChainSlot(UUID.randomUUID(), t, LINKED, UUID.randomUUID(), matchFinished = true, matchOpen = false)
        val m = UUID.randomUUID()
        val next = ChainSlot(UUID.randomUUID(), base.plusMinutes(20), LINKED, m, matchFinished = false, matchOpen = true)

        assertEquals(ChainDecision.Activate(listOf(m)), ScheduleChain.decideNext(listOf(finished, next)))
    }
}
