package de.lambda9.ready2race.backend.app.captcha.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.responses.ApiError
import io.ktor.http.*

enum class CaptchaError : ServiceError {
    ChallengeNotFound,
    WrongSolution;

    override fun respond(): ApiError = when (this) {
        // todo: other Error Codes than "BadRequest"?
        ChallengeNotFound -> ApiError(status = HttpStatusCode.BadRequest, message = "Captcha Challenge not found")
        WrongSolution -> ApiError(status = HttpStatusCode.BadRequest, message = "WrongSolution")
    }
}