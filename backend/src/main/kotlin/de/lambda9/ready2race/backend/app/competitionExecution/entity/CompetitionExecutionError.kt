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
    data object StartTimeNotSet : CompetitionExecutionError
    data object TeamWasPreviouslyDeregistered : CompetitionExecutionError

    // TODO: send out ErrorCodes for internationalization in frontend
    sealed interface ResultUploadError : CompetitionExecutionError {
        data object FileError : ResultUploadError
        data object NoHeaders : ResultUploadError
        data class ColumnUnknown(val expected: String) : ResultUploadError
        data class CellBlank(val row: Int, val column: String): ResultUploadError
        data class WrongCellType(val row: Int, val column: String, val actual: String, val expected: String) : ResultUploadError

        data class WrongTeamCount(val actual: Int, val expected: Int) : ResultUploadError

        sealed interface Invalid : ResultUploadError {

            data class DuplicatedStartNumbers(val duplicates: ValidationResult.Invalid.Duplicates) : Invalid
            data class DuplicatedPlaces(val duplicates: ValidationResult.Invalid.Duplicates) : Invalid

            data class PlacesUncontinuous(val actual: Int, val expected: Int) : Invalid

            data class Unexpected(val reason: ValidationResult.Invalid) : Invalid

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
            message = "The specified teams do not match the actual teams of the match"
        )

        RoundNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Round not found",
        )

        MatchResultsLocked -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Match results locked. Only results of the latest round can be edited.",
        )

        StartTimeNotSet -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "StartTime not set",
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