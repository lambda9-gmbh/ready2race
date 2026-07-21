package de.lambda9.ready2race.backend.app.raceclocker.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*

sealed interface RaceClockerError : ServiceError {

    /** No results URL is configured on the competition for the round this match belongs to. */
    data class UrlMissing(val qualification: Boolean) : RaceClockerError

    data class UrlInvalid(val url: String) : RaceClockerError

    data class Unreachable(val url: String, val reason: String) : RaceClockerError

    data class MalformedFeed(val reason: String) : RaceClockerError

    /**
     * The feed contains no rows for this match's wave. Either the start list for this heat has not
     * been imported into RaceClocker yet, or the wave was renamed there.
     */
    data class WaveNotFound(val wave: String) : RaceClockerError

    /**
     * RaceClocker is insert-only: importing the same start list twice creates duplicates rather than
     * updating. Writing results from an ambiguous feed would silently pick one of them, so we refuse.
     */
    data class DuplicateTeams(val wave: String?, val names: List<String>) : RaceClockerError

    /** Rows were found, but none of them carried a usable result yet. */
    data class NoResults(val wave: String?) : RaceClockerError

    /** The match has no name, so there is no wave to match the feed rows against. */
    data object MatchNameMissing : RaceClockerError

    override fun respond(): ApiError = when (this) {
        is UrlMissing -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = if (qualification) {
                "No RaceClocker results URL configured for the time trial race of this competition"
            } else {
                "No RaceClocker results URL configured for the heats race of this competition"
            },
            errorCode = ErrorCode.RACECLOCKER_URL_MISSING,
        )

        is UrlInvalid -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Not a valid RaceClocker results URL",
            details = mapOf("url" to url),
            errorCode = ErrorCode.RACECLOCKER_URL_INVALID,
        )

        is Unreachable -> ApiError(
            status = HttpStatusCode.BadGateway,
            message = "Could not load the RaceClocker results feed",
            details = mapOf("url" to url, "reason" to reason),
            errorCode = ErrorCode.RACECLOCKER_UNREACHABLE,
        )

        is MalformedFeed -> ApiError(
            status = HttpStatusCode.BadGateway,
            message = "The RaceClocker results feed could not be read",
            details = mapOf("reason" to reason),
            errorCode = ErrorCode.RACECLOCKER_MALFORMED_FEED,
        )

        is WaveNotFound -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No entries for this heat in the RaceClocker feed",
            details = mapOf("wave" to wave),
            errorCode = ErrorCode.RACECLOCKER_WAVE_NOT_FOUND,
        )

        is DuplicateTeams -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The RaceClocker feed contains duplicate entries for this heat",
            details = mapOf("wave" to wave, "teams" to names),
            errorCode = ErrorCode.RACECLOCKER_DUPLICATE_TEAMS,
        )

        is NoResults -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No timed results for this heat in the RaceClocker feed yet",
            details = mapOf("wave" to wave),
            errorCode = ErrorCode.RACECLOCKER_NO_RESULTS,
        )

        MatchNameMissing -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The match has no name, which is required to identify its RaceClocker wave",
        )
    }
}
