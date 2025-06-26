package de.lambda9.ready2race.backend.app.auth.boundary

import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.sessions.UserSession
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.auth() {
    route("/login") {
        rateLimit(RateLimitName("login")) {
            post {
                call.respondComprehension {
                    val body = !receiveKIO(LoginRequest.example)
                    AuthService.login(body) { token ->
                        sessions.set(UserSession(token))
                    }
                }
            }
        }

        get {
            call.respondComprehension {
                val token = sessions.get<UserSession>()?.token
                AuthService.checkLogin(token)
            }
        }

        delete {
            call.respondComprehension {
                val token = sessions.get<UserSession>()?.token
                AuthService.logout(token) {
                    sessions.clear<UserSession>()
                }
            }
        }
    }

    get("/privileges") {
        call.respondComprehension {
            !authenticate(Privilege.ReadUserGlobal)
            AuthService.getAllPrivileges()
        }
    }
}