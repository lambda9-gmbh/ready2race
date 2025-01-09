package de.lambda9.ready2race.backend.app.auth.boundary

import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.http.UserSession
import de.lambda9.ready2race.backend.plugins.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.auth() {
    rateLimit(RateLimitName("login")) {
        post("/login") {
            val login = call.receive<LoginRequest>()
            call.respondKIO {
                KIO.comprehension {
                    AuthService.login(login) { token ->
                        call.sessions.set(UserSession(token))
                    }
                }
            }
        }
    }
}