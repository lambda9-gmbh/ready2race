package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import de.lambda9.ready2race.backend.validation.ValidationResult
import io.ktor.http.*

sealed interface CompetitionExecutionError : ServiceError {
    data object MatchNotFound : CompetitionExecutionError
    data object MatchTeamNotFound : CompetitionExecutionError
    data object NoRoundsInSetup : CompetitionExecutionError
    data object AllRoundsCreated : CompetitionExecutionError
    data object NoSetupMatchesInRound : CompetitionExecutionError
    data object NoRegistrations : CompetitionExecutionError
    data object RegistrationsNotFinalized : CompetitionExecutionError
    data object NotEnoughTeamSpace : CompetitionExecutionError
    data object NotAllPlacesSet : CompetitionExecutionError
    data object TeamsNotMatching : CompetitionExecutionError
    data object RoundNotFound : CompetitionExecutionError
    data object MatchResultsLocked : CompetitionExecutionError

    /**
     * Ein Freilos: eine einzelne Mannschaft in einer nicht verpflichtenden Runde zieht weiter, ohne
     * zu fahren - es gibt kein Ergebnis einzutragen. Lief bislang unter [MatchResultsLocked] mit,
     * las sich für den Nutzer also als "nur die aktuelle Runde ist bearbeitbar" und schickte ihn
     * damit auf die falsche Fährte. RaceClocker trennt die beiden Fälle längst
     * (RaceClockerError.MatchIsBye), die Ergebniserfassung zieht hier nach.
     */
    data object MatchIsBye : CompetitionExecutionError

    /**
     * Die Umkehrung: "muss gefahren werden" (bye_must_race) lässt sich nur an einem Lauf setzen,
     * der überhaupt ein Freilos ist - an jedem anderen wäre das Flag wirkungslos und irreführend.
     */
    data object MatchIsNoBye : CompetitionExecutionError

    /** Beenden zurücknehmen setzt einen beendeten Lauf voraus - sonst gibt es nichts zurückzunehmen. */
    data object MatchNotFinished : CompetitionExecutionError

    /**
     * Ein Lauf lässt sich nur zurücksetzen, solange seine Folgerunde noch keine erzeugten Läufe
     * hat - dieselbe Stromrichtung wie beim Löschen der aktuellen Runde: Sobald aus den Ergebnissen
     * die nächste Runde gesät ist, würde der Reset einen Stand leeren, auf dem die Setzung der
     * Folgerunde bereits aufbaut. Eigener Fehler statt [MatchResultsLocked], weil die Abhilfe eine
     * andere ist: erst die Folgerunde löschen, dann zurücksetzen.
     */
    data object ResetBlockedByNextRound : CompetitionExecutionError
    data object StartTimeNotSet : CompetitionExecutionError

    /**
     * Ein Lauf ohne geplante Startzeit, wie ihn [StartlistMatchesWithoutStartTime] benennt -
     * Kürzel/Name des Wettkampfs, Runde und Laufname, damit der Nutzer den Lauf im Zeitplan
     * findet, ohne zu raten.
     */
    data class StartlistMatchWithoutStartTime(
        val matchId: java.util.UUID,
        val competitionIdentifier: String,
        val competitionShortName: String?,
        val competitionName: String?,
        val roundName: String,
        val matchName: String?,
    )

    /**
     * Der Startlisten-Sammelexport enthält Läufe ohne geplante Startzeit. Bewusst ALLE gesammelt
     * statt beim ersten abzubrechen ([StartTimeNotSet], 12.08.2026 per HAR belegt: ein nacktes
     * „StartTime not set" ohne Laufbezug ist am Renntag unbrauchbar). Der Export blockiert
     * weiterhin laut, statt still unvollständig zu liefern - abwählen kann der Nutzer die Läufe
     * über die Vorschau (matchIds), dann exportiert der Rest.
     */
    data class StartlistMatchesWithoutStartTime(
        val matches: List<StartlistMatchWithoutStartTime>,
    ) : CompetitionExecutionError
    data object TeamWasPreviouslyDeregistered : CompetitionExecutionError
    data object IsChallengeEvent : CompetitionExecutionError
    data object ResultConfirmationImageMissing : CompetitionExecutionError
    data object ResultDocumentNotFound : CompetitionExecutionError
    data object NotInChallengeTimespan : CompetitionExecutionError
    data object PlaceAndTimeBothNull : CompetitionExecutionError

    /**
     * Die vergebenen Plätze haben eine Lücke oder fangen nicht bei 1 an. [expected] ist der Platz,
     * der an dieser Stelle stehen müsste, [actual] der eingetragene - ohne beide Zahlen muss der
     * Nutzer die Liste selbst durchzählen, um die Lücke zu finden.
     */
    data class PlacesNotContinuous(val expected: Int, val actual: Int) : CompetitionExecutionError
    data object StartTimeManagedBySchedule : CompetitionExecutionError

    sealed interface ResultUploadError : CompetitionExecutionError {
        data object FileError : ResultUploadError
        data object NoHeaders : ResultUploadError
        data class ColumnUnknown(val expected: String) : ResultUploadError
        data class CellBlank(val row: Int, val column: String) : ResultUploadError
        data class WrongCellType(val row: Int, val column: String, val actual: String, val expected: String) :
            ResultUploadError

        data class UnparsableString(val row: Int, val column: String, val value: String) : ResultUploadError

        data class WrongTeamCount(val actual: Int, val expected: Int) : ResultUploadError

        sealed interface Invalid : ResultUploadError {

            data class DuplicatedStartNumbers(val duplicates: ValidationResult.Invalid.Duplicates) : Invalid
            data class DuplicatedTeams(val duplicates: ValidationResult.Invalid.Duplicates) : Invalid
            data class DuplicatedPlaces(val duplicates: ValidationResult.Invalid.Duplicates) : Invalid

            data class PlacesUncontinuous(val actual: Int, val expected: Int) : Invalid

            data class Unexpected(val reason: ValidationResult.Invalid) : Invalid

            data class DataInListIncomplete(val reason: ValidationResult.Invalid) : Invalid
            data class ResultNotFailedAndNoData(val reason: ValidationResult.Invalid) : Invalid

        }
    }

    override fun respond(): ApiError = when (this) {
        MatchNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Competition match not found",
        )

        MatchTeamNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Team not found",
        )

        NoRoundsInSetup -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Competition setup has no rounds defined",
        )

        AllRoundsCreated -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "All rounds have already been created",
        )

        NoSetupMatchesInRound -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No setup matches found for next round",
        )

        NoRegistrations -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No registrations for this competition",
        )

        RegistrationsNotFinalized -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Registrations for this competition have not been finalized",
        )

        NotEnoughTeamSpace -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "More registrations than the setup has allowed",
        )

        NotAllPlacesSet -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Not all places are set in the current round",
        )

        TeamsNotMatching -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The specified teams do not match the actual teams of the match",
            errorCode = ErrorCode.EXECUTION_TEAMS_NOT_MATCHING,
        )

        RoundNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Round not found",
        )

        MatchResultsLocked -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Match results locked. Only results of the latest round can be edited.",
            errorCode = ErrorCode.EXECUTION_MATCH_RESULTS_LOCKED,
        )

        MatchIsBye -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "This match is a bye - the team moves on without racing, there is no result to record.",
            errorCode = ErrorCode.EXECUTION_MATCH_IS_BYE,
        )

        MatchIsNoBye -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "This match is not a bye - 'must race' can only be set on a bye match.",
        )

        MatchNotFinished -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "This match is not finished - there is nothing to reopen.",
        )

        ResetBlockedByNextRound -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "This match cannot be reset: the following round has already been created from its results. Delete the following round first.",
            errorCode = ErrorCode.EXECUTION_RESET_BLOCKED_BY_NEXT_ROUND,
        )

        StartTimeNotSet -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "StartTime not set",
        )

        is StartlistMatchesWithoutStartTime -> ApiError(
            status = HttpStatusCode.Conflict,
            // Lesbarer Fallback für Klienten ohne Detail-Auswertung: „11 CF1x Viertelfinale VF2, …"
            message = "${matches.size} matches without a planned start time: " +
                matches.joinToString { match ->
                    listOfNotNull(
                        match.competitionIdentifier,
                        match.competitionShortName ?: match.competitionName,
                        match.roundName,
                        match.matchName,
                    ).joinToString(" ")
                },
            errorCode = ErrorCode.STARTLIST_MATCHES_WITHOUT_START_TIME,
            // Strukturiert fürs Frontend: die Liste als Detail-Feld, nicht nur als Satz.
            details = mapOf("matches" to matches),
        )

        TeamWasPreviouslyDeregistered -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Team has been deregistered before this round",
        )

        ResultUploadError.FileError -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Cannot read given file",
            errorCode = ErrorCode.FILE_ERROR
        )

        ResultUploadError.NoHeaders -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Cannot column headers, expected them in first row.",
            errorCode = ErrorCode.SPREADSHEET_NO_HEADERS
        )

        is ResultUploadError.Invalid.DataInListIncomplete -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Data in list is incomplete for some results.",
            errorCode = ErrorCode.LIST_DATA_INCOMPLETE
        )

        is ResultUploadError.Invalid.ResultNotFailedAndNoData -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Result is not marked as failed, but no data is given.",
            errorCode = ErrorCode.RESULT_NOT_FAILED_AND_NO_DATA
        )

        IsChallengeEvent -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Not allowed when the event is a challenge event"
        )

        ResultConfirmationImageMissing -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The result confirmation image is missing"
        )

        ResultDocumentNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Result document not found"
        )

        NotInChallengeTimespan -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Results can not be submitted outside of the challenge timespan."
        )

        PlaceAndTimeBothNull -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Results must have Places or Times completely filled out if not failed."
        )

        is PlacesNotContinuous -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The places are not continuous: expected $expected, got $actual.",
            errorCode = ErrorCode.EXECUTION_PLACES_NOT_CONTINUOUS,
            details = mapOf("expected" to expected, "actual" to actual),
        )

        StartTimeManagedBySchedule -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "Start time is managed by the event schedule",
            errorCode = ErrorCode.EXECUTION_START_TIME_MANAGED_BY_SCHEDULE,
        )

        is ResultUploadError.CellBlank -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Required value in row $row and column '$column' is missing.",
            errorCode = ErrorCode.SPREADSHEET_CELL_BLANK,
            details = mapOf("row" to row, "column" to column)
        )

        is ResultUploadError.ColumnUnknown -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Required column '$expected' is missing",
            errorCode = ErrorCode.SPREADSHEET_COLUMN_UNKNOWN,
            details = mapOf("expected" to expected)
        )

        is ResultUploadError.WrongCellType -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Wrong cell type in row $row and column '$column'; actual: $actual, expected: $expected.",
            errorCode = ErrorCode.SPREADSHEET_WRONG_CELL_TYPE,
            details = mapOf("row" to row, "column" to column, "expected" to expected, "actual" to actual)
        )

        is ResultUploadError.UnparsableString -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Cannot parse '$value' in row $row and column '$column'.",
            errorCode = ErrorCode.SPREADSHEET_UNPARSABLE_STRING,
            details = mapOf("row" to row, "column" to column, "value" to value)
        )

        is ResultUploadError.WrongTeamCount -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Wrong team count for this match; actual: $actual, expected: $expected.",
            errorCode = ErrorCode.WRONG_TEAM_COUNT,
            details = mapOf("actual" to actual, "expected" to expected)
        )

        is ResultUploadError.Invalid.DuplicatedPlaces -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "There are duplicate places in the given file.",
            errorCode = ErrorCode.DUPLICATE_PLACES,
            details = mapOf("reason" to duplicates)
        )

        is ResultUploadError.Invalid.DuplicatedStartNumbers -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "There are duplicate start numbers in the given file.",
            errorCode = ErrorCode.DUPLICATE_START_NUMBERS,
            details = mapOf("reason" to duplicates)
        )

        is ResultUploadError.Invalid.DuplicatedTeams -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "There are duplicate team identifiers in the given file.",
            errorCode = ErrorCode.DUPLICATE_TEAMS,
            details = mapOf("reason" to duplicates)
        )

        is ResultUploadError.Invalid.Unexpected -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Validation of given file unexpectedly failed",
            details = mapOf("reason" to reason)
        )

        is ResultUploadError.Invalid.PlacesUncontinuous -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Places are not continuous: actual: $actual, expected: $expected.",
            errorCode = ErrorCode.PLACES_UNCONTINUOUS,
            details = mapOf("actual" to actual, "expected" to expected)
        )
    }
}