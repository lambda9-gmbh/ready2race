package de.lambda9.ready2race.backend.app.auth.boundary

import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.ready2race.backend.sessions.UserSession
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.auth() {
    route("/login") {
        rateLimit(RateLimitName("login")) {
            post {
                val login = call.receiveV(LoginRequest.example)
                call.respondKIO {
                    AuthService.login(login.getOrThrow()) { token ->
                        sessions.set(UserSession(token))
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