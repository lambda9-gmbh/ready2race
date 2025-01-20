package de.lambda9.ready2race.backend.app.auth.boundary

import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.http.UserSession
import de.lambda9.ready2race.backend.plugins.respondKIO
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.auth() {
    route("/login") {
        rateLimit(RateLimitName("login")) {
            post {
                val login = call.receive<LoginRequest>()
                call.respondKIO {
                    AuthService.login(login) { token ->
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