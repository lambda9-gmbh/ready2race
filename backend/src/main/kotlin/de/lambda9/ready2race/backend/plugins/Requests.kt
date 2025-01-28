package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.validation.Validatable
import io.ktor.server.application.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.requestvalidation.*
import kotlin.time.Duration.Companion.minutes

fun Application.configureRequests() {
    install(DoubleReceive)
    install(RateLimit) {
        register(RateLimitName("login")) {
            rateLimiter(limit = 3, refillPeriod = 5.minutes)
            requestKey { call ->
                call.receiveV(LoginRequest.example).email
            }
        }
    }

    install(RequestValidation) {
        validate<Validatable> { validatable ->
            validatable.validate().toValidationResult()
        }
    }
}