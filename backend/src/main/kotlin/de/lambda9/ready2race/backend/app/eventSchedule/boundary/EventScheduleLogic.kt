package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleError
import de.lambda9.ready2race.backend.app.eventSchedule.entity.EventScheduleSlotState
import de.lambda9.ready2race.backend.app.eventSchedule.entity.ShiftTargetProblem
import de.lambda9.ready2race.backend.xls.XLSReadError
import java.time.Duration
import java.time.LocalDate
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
    /** Wettkampf-Kürzel (short_name) — für Platzhalter in kompakten Listen. */
    val competitionShortName: String? = null,
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
     * Ist der Lauf eines Slots schon unterwegs und damit der Absage entzogen? Zwei Quellen, eine
     * Antwort:
     * - [startedAt] ist der IST-Start. Er kommt aus der Zeitnahme und ist der harte Beleg.
     * - [activated] ist die Aktivierung durch den Schiedsrichter (`activated_at`). Sie geht dem
     *   Ist-Start voraus: zwischen "Boote gehen an den Start" und "RaceClocker meldet den Start"
     *   liegt ein Zeitfenster, in dem `started_at` noch leer ist, der Lauf aber längst passiert.
     *
     * Nur auf [startedAt] zu schauen, öffnete genau dieses Fenster für eine Absage - der Lauf war
     * danach abgesagt UND laufend zugleich, und Anzeige wie Dashboard zeigten ihn unverändert. Aus
     * Sicht des Schiedsrichters ist ein aktivierter Lauf gestartet, also zählt beides.
     *
     * Diese Regel bleibt bewusst an der Aktivierung, obwohl der Anzeigezustand seit dem
     * 09.08.2026 zwischen "in Vorbereitung" und "läuft" unterscheidet: Der Absage-Schutz greift
     * früher als die Anzeige, und genau das ist sein Zweck. Nur der Parametername ist mitgezogen
     * worden, damit im Backend nirgends mehr von "laufend" die Rede ist, wo Aktivierung gemeint
     * ist.
     */
    fun matchUnderway(startedAt: LocalDateTime?, activated: Boolean): Boolean =
        startedAt != null || activated

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
        competitionShortName: String? = null,
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
                competitionShortName = competitionShortName,
                roundName = roundName,
                matchName = matchName,
            )
        }
    }

    /**
     * Das Gegenstück zu [pendingSlotOrNull] für die andere Seite derselben Zeile: die ID des
     * ECHTEN Laufs, den ein abgesagter Slot verdeckt - oder null, wenn dieser Slot keinen
     * abgesagten Lauf trägt. [pendingSlotOrNull] löst nur den Fall "Runde noch nicht gesetzt"
     * (WAITING) auf; sobald die Runde gesetzt ist, gibt es einen Lauf, und der kennt die Absage
     * seines Slots nicht. Genau diese Läufe müssen Athleten-Anzeige und Kiosk aus "nächste Läufe"
     * herausnehmen - ein abgesagter Lauf steht sonst unverändert als nächster Start auf der
     * öffentlichen Anzeige.
     *
     * [matchExists] ist Bedingung, nicht nur Zustandszutat: ohne Lauf gibt es nichts
     * herauszufiltern (der Slot liefert dann schlicht keinen Platzhalter mehr). Die
     * Zustandsableitung bleibt die gemeinsame [deriveSlotState], damit "abgesagt" hier nicht
     * anders bewertet wird als im Zeitplan-Tab, in der Kette und beim Skip selbst.
     *
     * Bewusst OHNE Blick auf "läuft gerade": ein Lauf, der trotz Absage aktiv ist, gehört in den
     * Laufend-Block der Anzeige und nicht ins Nichts. Die Anzeige verschweigt keine Wirklichkeit,
     * sie nimmt nur den Plan zurück; dass dieser Zustand gar nicht erst entsteht, sichert die
     * Schutzregel in `EventScheduleService.setSlotSkipped`.
     */
    fun skippedMatchIdOrNull(
        setupMatchId: UUID?,
        skipped: Boolean,
        roundMaterialized: Boolean,
        matchExists: Boolean,
    ): UUID? {
        if (setupMatchId == null || !matchExists) {
            return null
        }

        val state = deriveSlotState(
            isFree = false,
            skipped = skipped,
            roundMaterialized = roundMaterialized,
            matchExists = true,
        )

        return if (state == EventScheduleSlotState.SKIPPED) setupMatchId else null
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
     * Der Index des ersten Slots, der wirklich HINTER [skippedStartTime] liegt - oder null, wenn es
     * ihn in [slots] nicht gibt. [slots] sind die Slots desselben Renntags hinter dem entfallenen
     * Slot, aufsteigend sortiert.
     *
     * "Echt später" statt "der nächste in der Liste" ist der ganze Punkt dieser Funktion: parallele
     * Slots (gleiche Startzeit wie der entfallene, z. B. zwei Läufe, die zusammen gestartet werden)
     * stehen in der sortierten Liste direkt dahinter, sind aber nicht "danach". Sie als Folgeslot zu
     * nehmen hieße, ein Delta von 0 zu messen und einen Block vorzuziehen, der gar nicht nachrückt.
     * Sie bleiben deshalb - wie der entfallene Slot selbst - an ihrer Zeit stehen.
     */
    fun firstFollowingIndex(slots: List<ShiftSlot>, skippedStartTime: LocalDateTime): Int? =
        slots.indexOfFirst { it.startTime > skippedStartTime }.takeIf { it >= 0 }

    /**
     * Das Zeitdelta, das ein entfallener Slot freigibt - oder null, wenn sich keins belastbar
     * ermitteln lässt und das Vorziehen deshalb gar nicht erst angeboten wird.
     *
     * Zwei Quellen in fester Rangfolge:
     * - [durationMinutes] ist die geplante Dauer des Slots, also die Angabe, die das Regattabüro
     *   selbst gepflegt hat. Sie geht vor, weil die Lücke zum Folgeslot mehr enthält als den Slot:
     *   Wendezeiten und Puffer stecken mit darin und sollen nach der Absage bestehen bleiben.
     * - [nextStartTime] ist die geplante Startzeit des ersten Slots danach (siehe
     *   [firstFollowingIndex]). Ohne gepflegte Dauer ist der Abstand zu ihm das Beste, was der Plan
     *   über die Länge des Entfallenen sagt.
     *
     * Eine gepflegte Dauer wird auch dann genommen, wenn sie GRÖSSER als die Lücke ist. Das Vorziehen
     * scheitert dann an der Vorgänger-Prüfung, mit einer konkreten Obergrenze in der Meldung - besser,
     * als still auf die Lücke zu kürzen und dem Nutzer eine Zahl unterzuschieben, die er nirgends
     * eingegeben hat.
     *
     * Eine Dauer von 0 oder weniger und eine Lücke von 0 (der Folgeslot beginnt zeitgleich, kann bei
     * [firstFollowingIndex] nicht vorkommen, hier aber der Vollständigkeit halber) sind kein Delta:
     * es gäbe nichts vorzuziehen.
     */
    fun advanceDeltaMinutes(
        durationMinutes: Int?,
        skippedStartTime: LocalDateTime,
        nextStartTime: LocalDateTime?,
    ): Long? {
        if (durationMinutes != null) {
            return durationMinutes.toLong().takeIf { it > 0 }
        }
        if (nextStartTime == null) {
            return null
        }
        return Duration.between(skippedStartTime, nextStartTime).toMinutes().takeIf { it > 0 }
    }

    /**
     * Zieht den Block [blockSlots] bis EINSCHLIESSLICH [targetSlotId] um [advanceMinutes] nach vorn;
     * alles hinter dem Ziel-Slot behält seine Zeit und wandert nur unverändert durch die Vorschau.
     * [blockSlots] beginnt beim ersten Slot, der nachrücken soll (siehe [firstFollowingIndex]), und
     * ist aufsteigend sortiert.
     *
     * Bewusst ein reines Blockverschieben statt einer Stauchung wie in [computeShift]: die Abstände
     * innerhalb des Blocks sind der gefahrene Rhythmus des Renntags (Wendezeiten, Ablauf am Steg) und
     * nicht der Puffer, aus dem das Delta kommt. Der Puffer entsteht stattdessen HINTER dem Ziel-Slot,
     * und genau dort ist er gewollt: Der Ziel-Slot ist die Stelle, ab der der Zeitplan wieder gelten
     * soll - typischerweise eine Mittagspause, die selbst mit vorrückt und sich dafür verlängert.
     *
     * [advanceMinutes] ist positiv gemeint (so viele Minuten NACH VORN); die Richtung steckt hier in
     * der Funktion, damit an der Aufrufstelle kein Vorzeichen zu drehen ist.
     */
    fun computeAdvance(
        blockSlots: List<ShiftSlot>,
        advanceMinutes: Long,
        targetSlotId: UUID,
    ): List<ShiftPreviewEntry> {
        val targetIndex = blockSlots.indexOfFirst { it.id == targetSlotId }
        require(targetIndex >= 0) { "target slot must be part of the advanceable block" }

        return blockSlots.mapIndexed { index, slot ->
            ShiftPreviewEntry(
                slotId = slot.id,
                oldStartTime = slot.startTime,
                newStartTime = if (index <= targetIndex) {
                    slot.startTime.minusMinutes(advanceMinutes)
                } else {
                    slot.startTime
                },
            )
        }
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

    /**
     * Prüft die beiden Eingaben des Modus "Aufholen bis" und benennt, welche davon nicht taugt -
     * oder null, wenn beide in Ordnung sind. Beide Fälle landeten früher in derselben Meldung,
     * verlangen vom Nutzer aber Gegensätzliches: einen anderen Ziel-Slot bzw. ein anderes Vorzeichen.
     * [daySlots] beginnt beim Start-Slot; der Ziel-Slot muss deshalb an Index > 0 liegen (Index 0 ist
     * der Start-Slot selbst, -1 heißt "gehört nicht zu diesem Renntag").
     */
    fun shiftTargetProblem(
        daySlots: List<ShiftSlot>,
        targetSlotId: UUID?,
        deltaMinutes: Long,
    ): ShiftTargetProblem? = when {
        deltaMinutes <= 0 -> ShiftTargetProblem.NEGATIVE_DELAY
        daySlots.indexOfFirst { it.id == targetSlotId } <= 0 -> ShiftTargetProblem.TARGET_NOT_AFTER_START
        else -> null
    }

    /**
     * Wie viele Minuten sich der Block höchstens vorziehen lässt, ohne [predecessorStartTime] zu
     * überholen - die Zahl, die dem Nutzer beim abgelehnten Vorziehen fehlt (B21). Maßgeblich ist der
     * Slot mit dem kleinsten Abstand zum Vorgänger, gemessen an seiner ALTEN Zeit; steht schon einer
     * davon auf oder vor dem Vorgänger, ist gar kein Vorziehen mehr möglich (0).
     */
    fun maxAdvanceMinutes(entries: List<ShiftPreviewEntry>, predecessorStartTime: LocalDateTime): Long =
        entries.minOfOrNull { Duration.between(predecessorStartTime, it.oldStartTime).toMinutes() }
            ?.coerceAtLeast(0) ?: 0

    /**
     * Der erste Slot, den die Verschiebung aus [day] hinausträgt - oder null, wenn alle im Renntag
     * bleiben. "Der erste" ist bewusst der zeitlich früheste Übertreter: er ist die Stelle, an der
     * der Nutzer die Verschiebung kappen muss, alle weiteren folgen ihm ohnehin.
     */
    fun firstEntryLeavingDay(entries: List<ShiftPreviewEntry>, day: LocalDate): ShiftPreviewEntry? =
        entries.filter { it.newStartTime.toLocalDate() != day }.minByOrNull { it.newStartTime }

    /**
     * Apache POI zählt Zeilen ab 0, Excel zeigt sie ab 1 an. [XLSReadError] trägt die POI-Nummer;
     * dem Regattabüro hilft nur die Nummer, die auch links im Tabellenblatt steht - bei einem
     * Zeitplan mit 100 Zeilen ist eine um eins verschobene Angabe schlimmer als gar keine.
     * (Kopfzeile = Excel-Zeile 1, erste Datenzeile = 2; genauso rechnet RowReader.rowNum, das die
     * Zeilennummern der Import-Vorschau liefert.)
     */
    fun excelRowNumber(poiRowNum: Int): Int = poiRowNum + 1

    /**
     * Übersetzt den präzisen Lesefehler des XLS-Lesers in den Zeitplan-Fehler, der ihn beim Nutzer
     * ankommen lässt. Früher fiel hier alles in ein pauschales "Import file could not be read" -
     * Zeile, Spalte und beanstandeter Wert gingen dabei verloren.
     */
    fun importErrorFor(error: XLSReadError): EventScheduleError = when (error) {
        XLSReadError.FileError -> EventScheduleError.ImportFileUnreadable
        XLSReadError.NoHeaders -> EventScheduleError.ImportNoHeaders
        is XLSReadError.CellError.ColumnUnknown -> EventScheduleError.ImportColumnMissing(error.expected)
        is XLSReadError.CellError.ParseError.CellBlank -> EventScheduleError.ImportCellBlank(
            row = excelRowNumber(error.row),
            column = error.col,
        )

        is XLSReadError.CellError.ParseError.WrongCellType -> EventScheduleError.ImportWrongCellType(
            row = excelRowNumber(error.row),
            column = error.col,
            actual = error.actual.name,
            expected = error.expected.name,
        )

        is XLSReadError.CellError.ParseError.UnparsableStringValue -> EventScheduleError.ImportCellUnparsable(
            row = excelRowNumber(error.row),
            column = error.col,
            value = error.value,
        )
    }
}
