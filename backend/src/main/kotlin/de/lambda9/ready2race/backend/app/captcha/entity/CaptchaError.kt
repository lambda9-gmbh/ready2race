package de.lambda9.ready2race.backend.app.captcha.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.ready2race.backend.responses.ErrorCode
import io.ktor.http.*

enum class CaptchaError : ServiceError {
    ChallengeNotFound,
    WrongSolution;

    override fun respond(): ApiError = when (this) {
        ChallengeNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Captcha Challenge not found")
        WrongSolution -> ApiError(status = HttpStatusCode.Conflict, message = "Wrong Solution", errorCode = ErrorCode.CAPTCHA_WRONG)
    }
}