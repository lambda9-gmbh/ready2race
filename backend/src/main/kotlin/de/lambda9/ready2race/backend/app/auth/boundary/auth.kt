package de.lambda9.ready2race.backend.app.auth.boundary

import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.ready2race.backend.sessions.UserSession
import de.lambda9.tailwind.core.KIO
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.auth() {
    route("/login") {
        rateLimit(RateLimitName("login")) {
            post {
                val payload = call.receiveV(LoginRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val body = !payload
                        AuthService.login(body) { token ->
                            sessions.set(UserSession(token))
                        }
                    }
                }
            }
        }

        get {
            call.respondKIO {
                val token = sessions.get<UserSession>()?.token
                AuthService.checkLogin(token)
            }
        }

        delete {
            call.respondKIO {
                val token = sessions.get<UserSession>()?.token
                AuthService.logout(token) {
                    sessions.clear<UserSession>()
                }
            }
        }
    }
}