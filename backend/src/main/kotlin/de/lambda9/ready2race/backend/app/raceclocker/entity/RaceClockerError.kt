package de.lambda9.ready2race.backend.app.raceclocker.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*

sealed interface RaceClockerError : ServiceError {

    /**
     * No RaceClocker race is selected for this competition - since 2026-08-11 a competition has
     * exactly one race for all of its rounds, so there is nothing to fall back to.
     */
    data object UrlMissing : RaceClockerError

    data class UrlInvalid(val url: String) : RaceClockerError

    data class Unreachable(val url: String, val reason: String) : RaceClockerError

    data class MalformedFeed(val reason: String) : RaceClockerError

    /**
     * The selected race contains no row for any team of this match. Either the start list for this
     * heat has not been imported into RaceClocker yet, or it was exported before the round was
     * re-created and carries identifiers that no longer exist.
     *
     * [urls] and [raceNames] stay list-shaped (with at most one entry each) so the error payload
     * keeps its wire format; the race name is what an operator can act on at the regatta, a bare
     * URL is not.
     */
    data class MatchNotInFeed(val urls: List<String>, val raceNames: List<String>) : RaceClockerError

    /**
     * RaceClocker is insert-only: importing the same start list twice creates duplicates rather than
     * updating. Writing results from an ambiguous feed would silently pick one of them, so we refuse.
     */
    data class DuplicateTeams(val wave: String?, val names: List<String>) : RaceClockerError

    /** Rows were found, but none of them carried a usable result yet. */
    data class NoResults(val wave: String?) : RaceClockerError

    /**
     * The match is a bye: a single team moved on through the bracket without racing. There is nothing
     * to time and therefore nothing to pull.
     */
    data object MatchIsBye : RaceClockerError

    override fun respond(): ApiError = when (this) {
        UrlMissing -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No RaceClocker results URL configured for this competition",
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

        is MatchNotInFeed -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "No entries for this heat in the RaceClocker feed",
            details = mapOf("urls" to urls, "races" to raceNames),
            errorCode = ErrorCode.RACECLOCKER_MATCH_NOT_IN_FEED,
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

        MatchIsBye -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "This heat is a bye - the team moves on without racing, so there are no results to pull",
            errorCode = ErrorCode.RACECLOCKER_MATCH_IS_BYE,
        )
    }
}
