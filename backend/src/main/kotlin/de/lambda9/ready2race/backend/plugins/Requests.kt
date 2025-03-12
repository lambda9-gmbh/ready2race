package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.app.appuser.entity.PasswordResetInitRequest
import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrThrow
import io.ktor.server.application.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes

// todo: @refactor: remove as middleware and implement own version
fun Application.configureRequests() {
    install(DoubleReceive)
    install(RateLimit) {
        register(RateLimitName("login")) {
            rateLimiter(limit = 3, refillPeriod = 5.minutes)
            requestKey { call ->
                call.receiveKIO(LoginRequest.example).unsafeRunSync().getOrThrow()?.email ?: "" // todo: @temporary
            }
        }
        register(RateLimitName("resetPassword")){
            rateLimiter(limit = 5, refillPeriod = 5.minutes)
            requestKey { call ->
                call.receiveKIO(PasswordResetInitRequest.example).unsafeRunSync().getOrThrow()?.email ?: "" // todo: @temporary
            }
        }
    }
}