package de.lambda9.ready2race.backend.app.club.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class ClubNameRuleError : ServiceError {
    RuleNotFound,

    /** Wortpaar ohne Bestandteil oder ohne Kürzel - beides ergäbe eine Regel, die nichts tut. */
    TermMissing,

    /**
     * Derselbe Bestandteil ein zweites Mal. Wortgenaue Ersetzung heißt: die zweite Zeile greift
     * nie, weil die erste den Bestandteil schon aufgebraucht hat. Sie wäre ein stiller Irrtum.
     */
    DuplicateTerm,
    ;

    override fun respond(): ApiError = when (this) {
        RuleNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Club name rule not found")
        TermMissing -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "A word pair needs both a term and a replacement",
        )

        DuplicateTerm -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "A rule for this term already exists",
        )
    }
}
