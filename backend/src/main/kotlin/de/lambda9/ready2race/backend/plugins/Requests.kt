package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.app.appuser.entity.PasswordResetInitRequest
import de.lambda9.ready2race.backend.app.appuser.entity.PasswordResetRequest
import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.plugins.requests.validation.ValidatableValidation
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrThrow
import io.ktor.server.application.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes

fun Application.configureRequests() {
    install(DoubleReceive)
    install(RateLimit) {
        register(RateLimitName("login")) {
            rateLimiter(limit = 3, refillPeriod = 5.minutes)
            requestKey { call ->
                call.receiveV(LoginRequest.example).unsafeRunSync().getOrThrow().email
            }
        }
        register(RateLimitName("resetPassword")){
            rateLimiter(limit = 5, refillPeriod = 5.minutes) // todo: is this rateLimiter necessary? And is 5/5 a good limit? It is to prevent someone from spamming emails with password reset requests
            requestKey { call ->
                call.receiveV(PasswordResetInitRequest.example).unsafeRunSync().getOrThrow().email
            }
        }
    }

    install(ValidatableValidation)
}