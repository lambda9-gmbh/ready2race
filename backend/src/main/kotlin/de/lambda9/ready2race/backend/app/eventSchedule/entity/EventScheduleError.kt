package de.lambda9.ready2race.backend.app.eventSchedule.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Warum ein "Aufholen bis" abgelehnt wird. Beide Fälle betreffen dieselbe Eingabemaske, verlangen
 * vom Nutzer aber Gegensätzliches (anderer Ziel-Slot vs. anderes Vorzeichen beim Verzug) - deshalb
 * reisen sie maschinenlesbar mit, statt in einem gemeinsamen Text zu verschwimmen.
 */
enum class ShiftTargetProblem {
    /** Der Ziel-Slot liegt nicht (mehr) hinter dem Start-Slot desselben Renntags. */
    TARGET_NOT_AFTER_START,

    /** Der angegebene Verzug ist 0 oder negativ - aufholen lässt sich nur eine Verspätung. */
    NEGATIVE_DELAY,
}

sealed interface EventScheduleError : ServiceError {
    data class EventNotFound(val eventId: UUID) : EventScheduleError
    data class SlotNotFound(val slotId: UUID) : EventScheduleError
    data class SetupMatchNotFound(val setupMatchId: UUID) : EventScheduleError
    data class SetupMatchAlreadyPlanned(val setupMatchId: UUID) : EventScheduleError
    data class MatchAlreadyStarted(val slotId: UUID) : EventScheduleError
    /** activateSlot (C1) - ein beendeter Lauf darf nicht wieder aktiviert werden, sonst erscheint er mit altem finished_at als laufend. */
    data class MatchAlreadyFinished(val slotId: UUID) : EventScheduleError
    data class SlotNotSkippable(val slotId: UUID) : EventScheduleError
    /**
     * setSlotSkipped - Programmpunkte (FREE-Slots) sagt nur die Orga ab (UPDATE EVENT), nicht das
     * Schiedsrichter-Dashboard: Pausen und Siegerehrungen sind Veranstaltungsorganisation, kein
     * Renngeschehen.
     */
    data class FreeSlotSkipReservedForOffice(val slotId: UUID) : EventScheduleError
    /** finish/activate über den Zeitplan (C1) - der Slot muss LINKED sein, sonst gibt es keinen Lauf. */
    data class SlotNotLinked(val slotId: UUID) : EventScheduleError
    data class CompressionImpossible(val maxReductionMinutes: Long) : EventScheduleError

    // --- Ablehnungsgründe des Verschiebe-Dialogs (B4/B21) ---
    // Früher waren das vier Wege in ein und dasselbe "Shift request parameters are inconsistent".
    // Das Regattabüro sah am Renntag in allen vier Fällen denselben Satz und musste raten, was zu
    // ändern ist. Jeder Grund hat deshalb jetzt einen eigenen ErrorCode und - wo es beim Korrigieren
    // hilft - strukturierte Werte (Grenzzeit, betroffener Slot), so wie bei CompressionImpossible.

    /** Die gewählte Verschiebung ergibt 0 Minuten Unterschied - es gäbe nichts zu speichern. */
    data object ShiftWithoutChange : EventScheduleError

    /** "Aufholen bis" mit unbrauchbarem Ziel-Slot oder unbrauchbarem Verzug. */
    data class ShiftTargetInvalid(val problem: ShiftTargetProblem) : EventScheduleError

    /**
     * Ein Slot würde durch die Verschiebung auf den Folgetag rutschen. Ein Shift bleibt bewusst im
     * Renntag - sonst wäre der Plan des nächsten Tages still mitbetroffen.
     */
    data class ShiftLeavesRaceDay(
        val slotId: UUID,
        val newStartTime: LocalDateTime,
        val raceDay: LocalDate,
    ) : EventScheduleError

    /**
     * Ein Vorziehen, das den unverschobenen Vorgänger-Slot überholen würde. [earliestStartTime] ist
     * die Startzeit dieses Vorgängers und damit die Untergrenze; [maxAdvanceMinutes] sagt, wie viele
     * Minuten das Vorziehen höchstens betragen darf.
     */
    data class ShiftOvertakesPredecessor(
        val earliestStartTime: LocalDateTime,
        val maxAdvanceMinutes: Long,
    ) : EventScheduleError

    // --- Vorziehen nach einem entfallenen Slot ---

    /**
     * Der Slot, hinter dem vorgezogen werden soll, ist gar nicht abgesagt. Das Vorziehen setzt genau
     * dort an, wo eine Absage Zeit frei gemacht hat - ohne Absage gibt es nichts, was nachrücken
     * könnte, und das Angebot käme aus einem veralteten Zustand des Zeitplan-Tabs.
     */
    data class SlotNotSkipped(val slotId: UUID) : EventScheduleError

    /**
     * Aus dem entfallenen Slot lässt sich kein belastbares Delta ableiten: keine gepflegte Dauer UND
     * kein Folgeslot am selben Renntag, hinter dem etwas nachrücken könnte (siehe
     * [de.lambda9.ready2race.backend.app.eventSchedule.boundary.EventScheduleLogic.advanceDeltaMinutes]).
     * Der Zeitplan lässt sich dann nur von Hand über das Verschieben-Werkzeug anpassen.
     */
    data class AdvanceDeltaUndeterminable(val slotId: UUID) : EventScheduleError

    data class DuplicateImportRow(val rowNumbers: List<Int>) : EventScheduleError

    // --- Lesefehler des Excel-Imports ---
    // Der XLS-Leser weiß genau, welche Zelle nicht lesbar war; früher landete alles davon in einem
    // pauschalen "Import file could not be read". Die Varianten spiegeln XLSReadError 1:1 und
    // benutzen dieselben SPREADSHEET_*-Codes wie der Ergebnis-Upload, damit das Frontend die
    // vorhandenen Übersetzungen (common.error.upload.*) weiterverwenden kann.
    // [row] ist immer die in Excel angezeigte Zeilennummer (Kopfzeile = 1, erste Datenzeile = 2).

    /** Die Datei ließ sich gar nicht als Arbeitsmappe öffnen. */
    data object ImportFileUnreadable : EventScheduleError

    /** Erste Zeile ohne verwertbare Spaltenüberschriften. */
    data object ImportNoHeaders : EventScheduleError

    data class ImportColumnMissing(val column: String) : EventScheduleError
    data class ImportCellBlank(val row: Int, val column: String) : EventScheduleError
    data class ImportWrongCellType(
        val row: Int,
        val column: String,
        val actual: String,
        val expected: String,
    ) : EventScheduleError

    data class ImportCellUnparsable(val row: Int, val column: String, val value: String) : EventScheduleError

    /** setRoundSkipped (Wettkampf → Durchführung) - die Runde hat noch keine Läufe (competition_match), es gibt nichts, was "entfallen" könnte; die einzelnen Slots sind stattdessen individuell zu überspringen. */
    data class RoundNotMaterialized(val setupRoundId: UUID) : EventScheduleError

    /**
     * setRoundSkipped - mindestens ein Lauf der Runde hat noch 2+ tatsächlich fahrende Mannschaften;
     * diese müssen ausgetragen werden, damit die nächste Runde sauber ausgelost werden kann.
     * [raceableMatchCount] ist die Anzahl dieser Läufe und steht in der Meldung: "noch 3 Läufe" sagt
     * dem Regattabüro, wie weit es vom Ziel entfernt ist, "es sind noch Läufe offen" nicht.
     */
    data class RoundHasRunsToRace(val setupRoundId: UUID, val raceableMatchCount: Int) : EventScheduleError

    override fun respond(): ApiError = when (this) {
        is EventNotFound -> ApiError(HttpStatusCode.NotFound, "Event with id $eventId not found")
        is SlotNotFound -> ApiError(HttpStatusCode.NotFound, "Schedule slot $slotId not found")
        is SetupMatchNotFound -> ApiError(HttpStatusCode.NotFound, "Setup match $setupMatchId not found in this event")
        is SetupMatchAlreadyPlanned -> ApiError(
            HttpStatusCode.Conflict,
            "Setup match $setupMatchId already has a schedule slot",
            errorCode = ErrorCode.SCHEDULE_SETUP_MATCH_ALREADY_PLANNED,
        )

        is MatchAlreadyStarted -> ApiError(
            HttpStatusCode.Conflict,
            "The match of slot $slotId has already started",
            errorCode = ErrorCode.SCHEDULE_SLOT_MATCH_ALREADY_STARTED,
        )

        is MatchAlreadyFinished -> ApiError(
            HttpStatusCode.Conflict,
            "The match of slot $slotId is already finished",
            errorCode = ErrorCode.SCHEDULE_SLOT_MATCH_ALREADY_FINISHED,
        )

        is SlotNotSkippable -> ApiError(
            HttpStatusCode.Conflict,
            "Slot $slotId cannot be skipped in its current state",
            errorCode = ErrorCode.SCHEDULE_SLOT_NOT_SKIPPABLE,
        )

        is FreeSlotSkipReservedForOffice -> ApiError(
            HttpStatusCode.Forbidden,
            "Slot $slotId is a program item - only the event office may skip or restore it",
        )

        is SlotNotLinked -> ApiError(
            HttpStatusCode.Conflict,
            "Slot $slotId is not linked to a match",
            errorCode = ErrorCode.SCHEDULE_SLOT_NOT_LINKED,
        )

        is CompressionImpossible -> ApiError(
            HttpStatusCode.UnprocessableEntity,
            "Cannot compress: only $maxReductionMinutes minutes available",
            // Maschinenlesbar zusätzlich zum Freitext, damit das Frontend die Minutenzahl nicht mehr
            // aus der (übersetzbaren/änderbaren) Nachricht herausparsen muss (siehe common.ts,
            // parseMaxReductionMinutes/extractMaxReductionMinutes).
            details = mapOf("maxReductionMinutes" to maxReductionMinutes),
            errorCode = ErrorCode.SCHEDULE_COMPRESSION_IMPOSSIBLE,
        )

        ShiftWithoutChange -> ApiError(
            HttpStatusCode.UnprocessableEntity,
            "Shift of 0 minutes would not change anything - pick a different number of minutes or a different time",
            errorCode = ErrorCode.SCHEDULE_SHIFT_WITHOUT_CHANGE,
        )

        is ShiftTargetInvalid -> ApiError(
            HttpStatusCode.UnprocessableEntity,
            when (problem) {
                ShiftTargetProblem.TARGET_NOT_AFTER_START ->
                    "Compression target must be a slot after the start slot on the same race day"

                ShiftTargetProblem.NEGATIVE_DELAY ->
                    "Compression needs a positive delay - to move slots earlier use a negative plain shift instead"
            },
            details = mapOf("problem" to problem.name),
            errorCode = ErrorCode.SCHEDULE_SHIFT_TARGET_INVALID,
        )

        is ShiftLeavesRaceDay -> ApiError(
            HttpStatusCode.UnprocessableEntity,
            "Shift would move slot $slotId to $newStartTime and thus past race day $raceDay",
            details = mapOf(
                "slotId" to slotId.toString(),
                "newStartTime" to newStartTime.toString(),
                "raceDay" to raceDay.toString(),
            ),
            errorCode = ErrorCode.SCHEDULE_SHIFT_LEAVES_RACE_DAY,
        )

        is ShiftOvertakesPredecessor -> ApiError(
            HttpStatusCode.UnprocessableEntity,
            "Cannot move earlier than $earliestStartTime without overtaking the preceding slot " +
                "(at most $maxAdvanceMinutes minutes earlier)",
            details = mapOf(
                "earliestStartTime" to earliestStartTime.toString(),
                "maxAdvanceMinutes" to maxAdvanceMinutes,
            ),
            errorCode = ErrorCode.SCHEDULE_SHIFT_OVERTAKES_PREDECESSOR,
        )

        is SlotNotSkipped -> ApiError(
            HttpStatusCode.Conflict,
            "Slot $slotId is not cancelled - there is no freed time to move up to",
            errorCode = ErrorCode.SCHEDULE_SLOT_NOT_SKIPPED,
        )

        is AdvanceDeltaUndeterminable -> ApiError(
            HttpStatusCode.UnprocessableEntity,
            "Cannot derive a time delta from slot $slotId: it has no planned duration and no " +
                "following slot on the same race day",
            errorCode = ErrorCode.SCHEDULE_ADVANCE_NO_DELTA,
        )

        is DuplicateImportRow -> ApiError(
            HttpStatusCode.UnprocessableEntity,
            "Import contains duplicate matches in rows $rowNumbers",
            details = mapOf("rowNumbers" to rowNumbers),
            errorCode = ErrorCode.SCHEDULE_IMPORT_DUPLICATE_ROWS,
        )

        ImportFileUnreadable -> ApiError(
            HttpStatusCode.UnprocessableEntity,
            "Import file could not be read",
            errorCode = ErrorCode.FILE_ERROR,
        )

        ImportNoHeaders -> ApiError(
            HttpStatusCode.UnprocessableEntity,
            "Cannot find column headers, expected them in the first row",
            errorCode = ErrorCode.SPREADSHEET_NO_HEADERS,
        )

        is ImportColumnMissing -> ApiError(
            HttpStatusCode.UnprocessableEntity,
            "Required column '$column' is missing",
            details = mapOf("expected" to column),
            errorCode = ErrorCode.SPREADSHEET_COLUMN_UNKNOWN,
        )

        is ImportCellBlank -> ApiError(
            HttpStatusCode.UnprocessableEntity,
            "Required value in row $row and column '$column' is missing",
            details = mapOf("row" to row, "column" to column),
            errorCode = ErrorCode.SPREADSHEET_CELL_BLANK,
        )

        is ImportWrongCellType -> ApiError(
            HttpStatusCode.UnprocessableEntity,
            "Wrong cell type in row $row and column '$column'; actual: $actual, expected: $expected",
            details = mapOf("row" to row, "column" to column, "actual" to actual, "expected" to expected),
            errorCode = ErrorCode.SPREADSHEET_WRONG_CELL_TYPE,
        )

        is ImportCellUnparsable -> ApiError(
            HttpStatusCode.UnprocessableEntity,
            "Cannot parse '$value' in row $row and column '$column'",
            details = mapOf("row" to row, "column" to column, "value" to value),
            errorCode = ErrorCode.SPREADSHEET_UNPARSABLE_STRING,
        )

        // Gegensätzliche Ursachen, die sich lange denselben Frontend-Text teilten: einmal ist die
        // Runde noch NICHT gesetzt ("setz sie erst"), einmal ist sie gesetzt und hat noch etwas zu
        // fahren ("diese Läufe müssen gefahren werden"). Wer den falschen der beiden Sätze liest,
        // sucht am Renntag in der genau verkehrten Richtung - deshalb je ein eigener Code.
        is RoundNotMaterialized -> ApiError(
            HttpStatusCode.Conflict,
            "Round $setupRoundId has no runs yet - cancel its slots individually instead",
            errorCode = ErrorCode.SCHEDULE_ROUND_NOT_MATERIALIZED,
        )

        is RoundHasRunsToRace -> ApiError(
            HttpStatusCode.Conflict,
            "Round $setupRoundId still has $raceableMatchCount run(s) to race - they must be executed for seeding",
            details = mapOf("raceableMatchCount" to raceableMatchCount),
            errorCode = ErrorCode.SCHEDULE_ROUND_HAS_RUNS_TO_RACE,
        )
    }
}
