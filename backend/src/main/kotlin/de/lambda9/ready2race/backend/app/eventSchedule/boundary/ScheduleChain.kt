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
     * Startgruppe nicht auf. Vor der Trennung von Aktivierung und Ist-Start stand hier ein
     * einzelnes Aktiv-Flag, das beide Fälle zusammenwarf — und damit eine Startgruppe schon dann
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
     * Wandert den GANZEN Zeitplan der Veranstaltung von vorn nach hinten, gruppiert nach Startzeit —
     * parallele Starts gehören zusammen und entscheiden als Einheit, nicht die zufällige
     * Zeilenreihenfolge innerhalb derselben Startzeit.
     *
     * Die Kette hält an der ersten Gruppe, die noch etwas offen hat ("die Front"), und handelt genau
     * dort. Sie überholt nichts. Bis zum 10.08.2026 begann der Lauf stattdessen beim gerade
     * beendeten Slot, und eine Gruppe, die nichts mehr zu aktivieren hatte, wurde übergangen — auch
     * dann, wenn sie längst an den Start gerufen war. Das hat auf der Coastal-Regatta zwei Läufe
     * gleichzeitig in "In Vorbereitung" gestellt (der zweite Anstoß der Kette kommt aus der
     * Folgerunden-Automatik) und nach einem Freilos am Folgetag den Rest des laufenden Tages
     * übersprungen. Für jede Gruppe (aufsteigend sortiert):
     * - Enthält sie einen wartenden Slot, stoppt die Suche bewusst OHNE Fehler: Ein paralleler
     *   Start darf nicht halb losgeschickt werden. Die Kette wartet, bis die Runde gesetzt wird —
     *   createNewRound stößt sie dann wieder an (zweiter Auslöser). Das ist auch die Antwort auf
     *   "was, wenn der nächste Lauf noch nicht gesetzt ist": warten, nicht weiterspringen. Wer den
     *   Slot nicht mehr fahren will, sagt ihn ab (`setSlotSkipped`) — das ist der Weg an einem
     *   wartenden Slot vorbei, und er stößt die Kette selbst wieder an.
     * - Läuft in dieser Gruppe noch ein anderer (paralleler) Lauf, der weder beendet noch
     *   geschlossen ist, wird NICHTS getan: die Regel "die ganze Startgruppe muss fertig sein,
     *   bevor die nächste losgeht" (Vorgabe von Thomas) bedeutet, dass der zuletzt fertige Lauf der
     *   Gruppe den Vorstoß auslöst, nicht der erste. Ohne diese Prüfung würde ein einzeln beendeter
     *   Lauf sofort die nächste Gruppe aktivieren, während sein Parallel-Lauf noch läuft.
     *   "Noch laufend" meint dabei den IST-Start ([ChainSlot.matchStartedAt]), nicht die bloße
     *   Aktivierung: ein nur an den Start gerufener Nachbar hält die Kette nicht an.
     * - Sonst werden alle noch nicht aktivierten, aktivierbaren Läufe der Gruppe (LINKED, nicht
     *   beendet, noch offen) gemeinsam aktiviert.
     * - Ist danach in der Gruppe noch ein offener Lauf übrig, war er bereits an den Start gerufen:
     *   Hier ist Schluss. Diese Gruppe ist die nächste, die gefahren wird — die dahinter geht
     *   niemanden etwas an, solange sie nicht gefahren ist.
     * - Erst eine Gruppe, in der wirklich nichts mehr offen ist (nur übersprungene, entfallene,
     *   freie oder beendete/geschlossene Slots), wird übergangen.
     */
    fun decideNext(slots: List<ChainSlot>): ChainDecision {
        val groups = slots.groupBy { it.startTime }.toSortedMap()

        for ((_, group) in groups) {
            val hasWaiting = group.any { it.state == EventScheduleSlotState.WAITING }
            if (hasWaiting) {
                return ChainDecision.WaitingForRound
            }

            // Alles, was in dieser Gruppe noch aussteht. Ein beendeter oder durchgewerteter Lauf
            // gehört nicht dazu - er hält niemanden mehr auf.
            val pending = group.filter {
                it.state == EventScheduleSlotState.LINKED && !it.matchFinished && it.matchOpen
            }

            if (pending.any { it.matchStartedAt != null }) {
                return ChainDecision.NothingToDo
            }

            val activatable = pending.filter { it.matchActivatedAt == null }
            if (activatable.isNotEmpty()) {
                return ChainDecision.Activate(activatable.mapNotNull { it.matchId })
            }

            if (pending.isNotEmpty()) {
                // Schon gerufen, noch nicht gefahren: die Front steht hier.
                return ChainDecision.NothingToDo
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
     * Zeitstrahl-Modus von `LiveDashboardService.finishMatch`: Der beendete Lauf ist nur der Anlass,
     * nicht der Startpunkt — [ScheduleChain.decideNext] sucht die Front im ganzen Zeitplan. Bis zum
     * 10.08.2026 stand hier die Startzeit des beendeten Slots als Untergrenze; damit konnte ein
     * Beenden am zweiten Regattatag den offenen Rest des ersten Tages überholen.
     */
    fun decideAndActivate(eventId: UUID, userId: UUID): App<Nothing, Unit> =
        KIO.comprehension {
            val chainSlots = !buildChainSlots(eventId)
            !activate(ScheduleChain.decideNext(chainSlots), userId)
            KIO.unit
        }

    /**
     * Wieder ansetzen, wenn die Kette an einem wartenden Slot geparkt sein könnte: nach
     * Rundenerzeugung (`createNewRound`) und nach dem Überspringen eines wartenden Slots
     * (`EventScheduleService.setSlotSkipped`). Greift nur, wenn die Automatik überhaupt an ist.
     *
     * Eine Sperre "läuft schon etwas?" braucht es hier nicht mehr: Ein bereits gerufener oder
     * laufender Lauf IST die Front, an der [ScheduleChain.decideNext] von sich aus stehen bleibt.
     * Vorher hing das an einer zweiten Abfrage über die ganze Veranstaltung — und die entschied
     * über einen Zustand, den der Zeitplan selbst viel genauer kennt.
     */
    fun resumeIfParked(eventId: UUID, userId: UUID): App<Nothing, Unit> = KIO.comprehension {
        val mode = !EventRepo.getChainProgressionMode(eventId).orDie()
        if (mode == ChainProgressionMode.DEAKTIVIERT) {
            return@comprehension KIO.unit
        }

        // Resume greift erst, wenn schon ein verplanter Lauf beendet wurde — den allerersten Lauf
        // des Zeitplans aktiviert der Schiedsrichter wie bisher von Hand. Ohne diese Schranke würde
        // das Setzen der ersten Runde am Morgen das erste Rennen von selbst an den Start rufen.
        val somethingFinished = !EventScheduleRepo.getLastFinishedSlotTime(eventId).orDie()
        if (somethingFinished == null) {
            return@comprehension KIO.unit
        }

        val chainSlots = !buildChainSlots(eventId)
        !activate(ScheduleChain.decideNext(chainSlots), userId)

        KIO.unit
    }

    /** Liest alle Slots der Veranstaltung und leitet ihren Zustand wie in [EventScheduleService] ab. */
    private fun buildChainSlots(eventId: UUID): App<Nothing, List<ChainSlot>> =
        KIO.comprehension {
            val records = !EventScheduleRepo.getChainSlots(eventId).orDie()
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
