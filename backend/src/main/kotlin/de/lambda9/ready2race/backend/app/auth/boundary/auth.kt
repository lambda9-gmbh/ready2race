package de.lambda9.ready2race.backend.app.auth.boundary

import de.lambda9.ready2race.backend.app.auth.control.loginDto
import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.http.ApiResponse
import de.lambda9.ready2race.backend.http.UserSession
import de.lambda9.ready2race.backend.http.authenticate
import de.lambda9.ready2race.backend.plugins.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.auth() {
    route("/login") {
        rateLimit(RateLimitName("login")) {
            post {
                call.respondKIO {
                    val login = call.receive<LoginRequest>()
                    AuthService.login(login) { token ->
                        call.sessions.set(UserSession(token))
                    }
                }
            }
        }

        get {
            call.respondKIO {
                val token = call.sessions.get<UserSession>()?.token
                AuthService.checkLogin(token)
            }
        }

        delete {
            call.respondKIO {
                val token = call.sessions.get<UserSession>()?.token
                AuthService.logout(token) {
                    call.sessions.clear<UserSession>()
                }
            }
        }
    }
}