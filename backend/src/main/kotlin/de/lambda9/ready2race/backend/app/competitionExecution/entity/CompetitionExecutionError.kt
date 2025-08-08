package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.tailwind.core.Cause.Companion.expected
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

        ResultUploadError.FileError -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Cannot read given file"
        )
        ResultUploadError.NoHeaders -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Cannot column headers, expected them in first row."
        )

        is ResultUploadError.CellBlank -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Required value in row $row and column '$column' is missing.",
        )
        is ResultUploadError.ColumnUnknown -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Required column '$expected' is missing"
        )
        is ResultUploadError.WrongCellType -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Wrong cell type in row $row and column '$column'; actual: $actual, expected: $expected.",
        )

        is ResultUploadError.WrongTeamCount -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Wrong team count for this match; actual: $actual, expected: $expected.",
        )

        is ResultUploadError.Invalid.DuplicatedPlaces -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "There are duplicate places in the given file.",
            details = mapOf("duplicates" to duplicates)
        )
        is ResultUploadError.Invalid.DuplicatedStartNumbers -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "There are duplicate start numbers in the given file.",
            details = mapOf("duplicates" to duplicates)
        )
        is ResultUploadError.Invalid.Unexpected -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Validation of given file unexpectedly failed",
            details = mapOf("reason" to reason)
        )

        is ResultUploadError.Invalid.PlacesUncontinuous -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Places are not continuous: actual: $actual, expected: $expected.",
        )
    }
}