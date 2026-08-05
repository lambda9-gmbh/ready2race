package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

data class ShiftSlot(
    val id: UUID,
    val startTime: LocalDateTime,
    val durationMinutes: Int?,
)

data class ShiftPreviewEntry(
    val slotId: UUID,
    val oldStartTime: LocalDateTime,
    val newStartTime: LocalDateTime,
)

sealed interface ShiftResult {
    data class Ok(val entries: List<ShiftPreviewEntry>) : ShiftResult
    data class CompressionImpossible(val maxReductionMinutes: Long) : ShiftResult
}

/**
 * Ein WAITING-Slot mit den Namen aus der Setup-Zeile - die gemeinsame Grundlage für Platzhalter
 * auf Athleten-Anzeige und Live-Dashboard (siehe [EventScheduleLogic.pendingSlotOrNull]).
 * [setupMatchId] zeigt auf die Setup-Zeile, nicht auf einen echten Lauf, der für WAITING-Slots
 * per Definition noch nicht existiert; [slotId] ist die ID des Zeitstrahl-Slots selbst.
 */
data class PendingScheduleSlotInfo(
    val slotId: UUID,
    val setupMatchId: UUID,
    val startTime: LocalDateTime,
    val competitionId: UUID,
    val competitionName: String,
    val roundName: String?,
    val matchName: String?,
)

/**
 * Ein FREE-Slot (Programmpunkt wie "Mittagspause") mit seinem Namen - nur fürs Live-Dashboard
 * (siehe [EventScheduleLogic.freeSlotOrNull]). Athleten-Anzeige und Kiosk bleiben bei
 * [PendingScheduleSlotInfo]/WAITING-Slots; öffentliche Boards zeigen bewusst keine Pausen.
 */
data class FreeScheduleSlotInfo(
    val slotId: UUID,
    val startTime: LocalDateTime,
    val name: String?,
)

object EventScheduleLogic {

    const val MIN_GAP_MINUTES = 5L

    /**
     * Reihenfolge der Prüfungen ist fachlich: "entfällt" ist endgültig (die Runde existiert, der
     * Lauf kann nie mehr entstehen) und schlägt deshalb auch ein manuelles Überspringen.
     */
    fun deriveSlotState(
        isFree: Boolean,
        skipped: Boolean,
        roundMaterialized: Boolean,
        matchExists: Boolean,
    ): EventScheduleSlotState = when {
        !isFree && roundMaterialized && !matchExists -> EventScheduleSlotState.OBSOLETE
        skipped -> EventScheduleSlotState.SKIPPED
        isFree -> EventScheduleSlotState.FREE
        matchExists -> EventScheduleSlotState.LINKED
        else -> EventScheduleSlotState.WAITING
    }

    /**
     * Baut aus einer rohen Zeitstrahl-Zeile einen WAITING-Platzhalter, oder liefert null - für
     * FREE-Slots (keine Setup-Zeile/Kompetition), SKIPPED, LINKED und OBSOLETE. Ersetzt die früher
     * getrennt in Athleten-Anzeige und Live-Dashboard gepflegte "nur WAITING zählt"-Regel: beide
     * bauen jetzt auf dieser einen Funktion auf, statt den Zustand jeweils selbst zu prüfen.
     *
     * Bewusst ohne Alterung: Ein WAITING-Slot, der überfällig ist, wird hier weiterhin geliefert
     * und bleibt Platzhalter - anders als bei echten Läufen gibt es keine Nachfrist, nach der er
     * von der Anzeige verschwindet. Genau der überfällige Zeitpunkt ist der Punkt, an dem die
     * Boards ihn zeigen müssen; ein liegen gebliebener Slot wird erst durch das manuelle
     * Überspringen aufgelöst.
     */
    fun pendingSlotOrNull(
        slotId: UUID,
        setupMatchId: UUID?,
        startTime: LocalDateTime,
        competitionId: UUID?,
        competitionName: String?,
        roundName: String?,
        matchName: String?,
        skipped: Boolean,
        roundMaterialized: Boolean,
        matchExists: Boolean,
    ): PendingScheduleSlotInfo? {
        if (setupMatchId == null || competitionId == null) {
            return null
        }

        val state = deriveSlotState(
            isFree = false,
            skipped = skipped,
            roundMaterialized = roundMaterialized,
            matchExists = matchExists,
        )

        return if (state != EventScheduleSlotState.WAITING) {
            null
        } else {
            PendingScheduleSlotInfo(
                slotId = slotId,
                setupMatchId = setupMatchId,
                startTime = startTime,
                competitionId = competitionId,
                competitionName = competitionName ?: "",
                roundName = roundName,
                matchName = matchName,
            )
        }
    }

    /**
     * Baut aus einer rohen Zeitstrahl-Zeile einen FREE-Platzhalter (Programmpunkt), oder liefert
     * null - für Slots, die keine FREE-Slots sind, oder die übersprungen wurden. Getrennt von
     * [pendingSlotOrNull], weil beide Platzhalter-Arten unterschiedliche Konsumenten haben: WAITING
     * zeigen Athleten-Anzeige, Kiosk und Live-Dashboard gemeinsam, FREE nur das Live-Dashboard
     * (öffentliche Boards bekommen keine Pausen zu sehen).
     */
    fun freeSlotOrNull(
        slotId: UUID,
        isFree: Boolean,
        name: String?,
        startTime: LocalDateTime,
        skipped: Boolean,
    ): FreeScheduleSlotInfo? {
        if (!isFree) {
            return null
        }

        val state = deriveSlotState(
            isFree = true,
            skipped = skipped,
            roundMaterialized = false,
            matchExists = false,
        )

        return if (state != EventScheduleSlotState.FREE) {
            null
        } else {
            FreeScheduleSlotInfo(slotId = slotId, startTime = startTime, name = name)
        }
    }

    /**
     * [slots]: ab dem gewählten Start-Slot, aufsteigend sortiert, nur derselbe Renntag.
     * Ohne [targetSlotId] werden alle Slots stumpf um [deltaMinutes] verschoben. Mit Ziel-Slot
     * behält dieser seine Zeit; die Verspätung wird aus den Abständen davor herausgestaucht.
     * Untergrenze je Abstand: duration_minutes des vorderen Slots, mindestens [MIN_GAP_MINUTES].
     */
    fun computeShift(
        slots: List<ShiftSlot>,
        deltaMinutes: Long,
        targetSlotId: UUID?,
    ): ShiftResult {
        if (targetSlotId == null) {
            return ShiftResult.Ok(slots.map {
                ShiftPreviewEntry(it.id, it.startTime, it.startTime.plusMinutes(deltaMinutes))
            })
        }

        val targetIndex = slots.indexOfFirst { it.id == targetSlotId }
        require(targetIndex > 0) { "target slot must come after the shifted slot" }

        val gaps = (0 until targetIndex).map { i ->
            Duration.between(slots[i].startTime, slots[i + 1].startTime).toMinutes()
        }
        val floors = (0 until targetIndex).map { i ->
            maxOf(slots[i].durationMinutes?.toLong() ?: MIN_GAP_MINUTES, MIN_GAP_MINUTES)
        }
        val slacks = gaps.zip(floors) { gap, floor -> (gap - floor).coerceAtLeast(0) }
        val totalSlack = slacks.sum()
        if (totalSlack < deltaMinutes) {
            return ShiftResult.CompressionImpossible(maxReductionMinutes = totalSlack)
        }

        // Proportional stauchen, Rundungsrest von vorn nach hinten minutenweise verteilen.
        val reductions = slacks.map { slack ->
            if (totalSlack == 0L) 0L else deltaMinutes * slack / totalSlack
        }.toMutableList()
        var remainder = deltaMinutes - reductions.sum()
        var i = 0
        while (remainder > 0) {
            if (reductions[i] < slacks[i]) {
                reductions[i] = reductions[i] + 1
                remainder--
            }
            i = (i + 1) % reductions.size
        }

        val entries = mutableListOf(
            ShiftPreviewEntry(slots[0].id, slots[0].startTime, slots[0].startTime.plusMinutes(deltaMinutes))
        )
        for (idx in 0 until targetIndex) {
            val newGap = gaps[idx] - reductions[idx]
            entries.add(
                ShiftPreviewEntry(
                    slots[idx + 1].id,
                    slots[idx + 1].startTime,
                    entries[idx].newStartTime.plusMinutes(newGap),
                )
            )
        }
        // Slots hinter dem Ziel bleiben unangetastet, tauchen aber in der Vorschau auf.
        for (idx in targetIndex + 1 until slots.size) {
            entries.add(ShiftPreviewEntry(slots[idx].id, slots[idx].startTime, slots[idx].startTime))
        }
        return ShiftResult.Ok(entries)
    }

    /**
     * Guard gegen "Überholen des Vorgängers" bei einem negativen Shift (Vorziehen):
     * [predecessorStartTime] ist die Startzeit des letzten UNverschobenen Slots desselben Tages vor
     * dem Start-Slot - der bleibt an seiner Zeit stehen. Fällt eine der neuen Zeiten davor, würde der
     * verschobene Block zeitlich vor seinen Vorgänger rutschen und die Reihenfolge im Zeitstrahl
     * durcheinanderbringen; das ist kein zulässiges Ergebnis. Ohne Vorgänger am selben Tag (der
     * Start-Slot ist der erste des Tages) gibt es von dieser Seite keine Grenze.
     */
    fun overtakesPredecessor(entries: List<ShiftPreviewEntry>, predecessorStartTime: LocalDateTime?): Boolean {
        if (predecessorStartTime == null) {
            return false
        }
        return entries.any { it.newStartTime < predecessorStartTime }
    }
}
