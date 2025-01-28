package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.validation.Validatable
import io.ktor.server.application.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.request.*
import kotlin.time.Duration.Companion.minutes

fun Application.configureRequests() {
    install(DoubleReceive)
    install(RateLimit) {
        register(RateLimitName("login")) {
            rateLimiter(limit = 3, refillPeriod = 5.minutes)
            requestKey {
                it.receive<LoginRequest>().email
            }
        }
    }

    install(RequestValidation) {
        validate<Validatable> { validatable ->
            validatable.validate().toValidationResult()
        }
    }
}