package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.ChainProgressionMode
import de.lambda9.ready2race.backend.app.eventSchedule.control.EventScheduleRepo
import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_SCHEDULE_SLOT
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.UUID

data class ChainSlot(
    val slotId: UUID,
    val startTime: LocalDateTime,
    val state: EventScheduleSlotState,
    val matchId: UUID?,
    val matchFinished: Boolean,
    val matchOpen: Boolean,
    /**
     * Wann der Lauf an den Start gerufen wurde (competition_match.activated_at). Entscheidet in
     * [ScheduleChain.decideNext], ob er noch aktivierbar ist — ein bereits aktivierter Lauf soll
     * nicht ein zweites Mal aktiviert werden.
     */
    val matchActivatedAt: LocalDateTime? = null,
    /**
     * Der Ist-Start (competition_match.started_at). NUR er blockiert das Vorrücken: Ein Lauf, den
     * die Kette an den Start gerufen hat, dessen Boote aber noch am Steg liegen, hält die nächste
     * Startgruppe nicht auf. Vor der Trennung von Aktivierung und Ist-Start stand hier
     * `currentlyRunning`, das beide Fälle zusammenwarf — und damit eine Startgruppe schon dann
     * blockierte, wenn sie nur gerufen war.
     */
    val matchStartedAt: LocalDateTime? = null,
)

sealed interface ChainDecision {
    data class Activate(val matchIds: List<UUID>) : ChainDecision
    data object WaitingForRound : ChainDecision
    data object NothingToDo : ChainDecision
}

object ScheduleChain {

    /**
     * Wandert die Slots ab dem beendeten Lauf (einschließlich seiner eigenen Gruppe, siehe
     * [EventScheduleRepo.getChainSlots]) vorwärts, gruppiert nach Startzeit — parallele Starts
     * gehören zusammen und entscheiden als Einheit, nicht die zufällige Zeilenreihenfolge innerhalb
     * derselben Startzeit. Für jede Gruppe (aufsteigend sortiert):
     * - Enthält sie einen wartenden Slot, stoppt die Suche bewusst OHNE Fehler: Ein paralleler
     *   Start darf nicht halb losgeschickt werden. Die Kette wartet, bis die Runde gesetzt wird —
     *   createNewRound stößt sie dann wieder an (zweiter Auslöser).
     * - Läuft in dieser Gruppe noch ein anderer (paralleler) Lauf, der weder beendet noch
     *   geschlossen ist, wird NICHTS getan: die Regel "die ganze Startgruppe muss fertig sein,
     *   bevor die nächste losgeht" (Vorgabe von Thomas) bedeutet, dass der zuletzt fertige Lauf der
     *   Gruppe den Vorstoß auslöst, nicht der erste. Ohne diese Prüfung würde ein einzeln beendeter
     *   Lauf sofort die nächste Gruppe aktivieren, während sein Parallel-Lauf noch läuft.
     *   "Noch laufend" meint dabei den IST-Start ([ChainSlot.matchStartedAt]), nicht die bloße
     *   Aktivierung: ein nur an den Start gerufener Nachbar hält die Kette nicht an.
     * - Sonst werden alle noch nicht aktivierten, aktivierbaren Läufe der Gruppe (LINKED, nicht
     *   beendet, noch offen, noch nicht laufend) gemeinsam aktiviert.
     * - Hat die Gruppe nur übersprungene, entfallene, freie oder bereits erledigte/geschlossene
     *   Slots, wird sie übergangen und die nächste Gruppe betrachtet.
     */
    fun decideNext(slotsAfter: List<ChainSlot>): ChainDecision {
        val groups = slotsAfter.groupBy { it.startTime }.toSortedMap()

        for ((_, group) in groups) {
            val hasWaiting = group.any { it.state == EventScheduleSlotState.WAITING }
            if (hasWaiting) {
                return ChainDecision.WaitingForRound
            }

            val siblingStillRunning = group.any {
                it.state == EventScheduleSlotState.LINKED && !it.matchFinished && it.matchOpen &&
                    it.matchStartedAt != null
            }
            if (siblingStillRunning) {
                return ChainDecision.NothingToDo
            }

            val activatable = group.filter {
                it.state == EventScheduleSlotState.LINKED && !it.matchFinished && it.matchOpen &&
                    it.matchActivatedAt == null
            }
            if (activatable.isNotEmpty()) {
                return ChainDecision.Activate(activatable.mapNotNull { it.matchId })
            }
            // Nur FREE/SKIPPED/OBSOLETE bzw. beendete/geschlossene Läufe in dieser Gruppe — weiter.
        }
        return ChainDecision.NothingToDo
    }
}

/**
 * Der Zeitstrahl-Modus der Aktivierungskette (Task 9): [decideAndActivate] ist der Auslöser aus
 * `finishMatch`, [resumeIfParked] der zweite Auslöser aus `createNewRound` und aus
 * `EventScheduleService.setSlotSkipped` — alle teilen sich `buildChainSlots`/
 * `ScheduleChain.decideNext`/die Aktivierung, damit an keiner Stelle zweimal dieselbe Entscheidung
 * anders getroffen wird.
 */
object ScheduleChainService {

    /**
     * Zeitstrahl-Modus von `LiveDashboardService.finishMatch`: [after] ist die Startzeit des Slots
     * des gerade beendeten Laufs.
     */
    fun decideAndActivate(eventId: UUID, after: LocalDateTime, userId: UUID): App<Nothing, Unit> =
        KIO.comprehension {
            val chainSlots = !buildChainSlots(eventId, after)
            !activate(ScheduleChain.decideNext(chainSlots), userId)
            KIO.unit
        }

    /**
     * Wieder ansetzen, wenn die Kette an einem wartenden Slot geparkt sein könnte: nach
     * Rundenerzeugung (`createNewRound`) und nach dem Überspringen eines wartenden Slots
     * (`EventScheduleService.setSlotSkipped`). Greift nur, wenn die Automatik an ist und gerade
     * kein Lauf des Events aktiv ist — sonst greift entweder schon ein Lauf, oder die Veranstaltung
     * will die Kette gar nicht.
     */
    fun resumeIfParked(eventId: UUID, userId: UUID): App<Nothing, Unit> = KIO.comprehension {
        val mode = !EventRepo.getChainProgressionMode(eventId).orDie()
        if (mode == ChainProgressionMode.DEAKTIVIERT) {
            return@comprehension KIO.unit
        }

        val alreadyRunning = !EventScheduleRepo.hasRunningMatch(eventId).orDie()
        if (alreadyRunning) {
            return@comprehension KIO.unit
        }

        // Resume greift erst, wenn schon ein verplanter Lauf beendet wurde — den allerersten Lauf
        // aktiviert der Schiedsrichter wie bisher von Hand. Ohne Referenzpunkt gibt es also nichts
        // zu tun (und insbesondere keinen Fallback auf LocalDateTime.MIN, der beim jOOQ-Bind auf
        // Timestamp.valueOf() mit "timestamp out of range" gegen Postgres krachen würde).
        val reference = !EventScheduleRepo.getLastFinishedSlotTime(eventId).orDie()
        if (reference == null) {
            return@comprehension KIO.unit
        }

        val chainSlots = !buildChainSlots(eventId, reference)
        !activate(ScheduleChain.decideNext(chainSlots), userId)

        KIO.unit
    }

    /** Liest die Slots nach [after] und leitet ihren Zustand wie in [EventScheduleService] ab. */
    private fun buildChainSlots(eventId: UUID, after: LocalDateTime): App<Nothing, List<ChainSlot>> =
        KIO.comprehension {
            val records = !EventScheduleRepo.getChainSlots(eventId, after).orDie()
            KIO.ok(records.map { r ->
                val isFree = r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] == null
                val matchExists = r.get("match_exists", Boolean::class.java) == true
                ChainSlot(
                    slotId = r[EVENT_SCHEDULE_SLOT.ID]!!,
                    startTime = r[EVENT_SCHEDULE_SLOT.START_TIME]!!,
                    state = EventScheduleLogic.deriveSlotState(
                        isFree = isFree,
                        skipped = r[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null,
                        roundMaterialized = r.get("round_materialized", Boolean::class.java) == true,
                        matchExists = matchExists,
                    ),
                    matchId = if (matchExists) r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] else null,
                    matchFinished = r.get("match_finished_at", LocalDateTime::class.java) != null,
                    matchOpen = r.get("match_open", Boolean::class.java) == true,
                    matchActivatedAt = r[COMPETITION_MATCH.ACTIVATED_AT],
                    matchStartedAt = r[COMPETITION_MATCH.STARTED_AT],
                )
            })
        }

    private fun activate(decision: ChainDecision, userId: UUID): App<Nothing, Unit> = when (decision) {
        is ChainDecision.Activate -> decision.matchIds.traverse { activate(it, userId) }.map { }
        ChainDecision.WaitingForRound, ChainDecision.NothingToDo -> KIO.unit
    }

    private fun activate(matchId: UUID, userId: UUID): App<Nothing, Unit> =
        CompetitionMatchRepo.update(matchId) {
            // Die Kette ruft an den Start, sie startet nicht: nur activatedAt, und nur beim ersten
            // Mal - ein erneuter Kettenlauf soll den Zeitpunkt nicht vorrücken.
            if (activatedAt == null) {
                activatedAt = LocalDateTime.now()
            }
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().map { }
}
