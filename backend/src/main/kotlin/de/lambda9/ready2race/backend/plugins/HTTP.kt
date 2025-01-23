package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.Config
import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.http.UserSession
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import kotlin.time.Duration.Companion.minutes

fun Application.configureHTTP(mode: Config.Mode) {
    install(ForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
    install(XForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }

    if (mode == Config.Mode.DEV) {
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowCredentials = true
        }
    }

    install(DoubleReceive)
    install(RateLimit) {
        register(RateLimitName("login")) {
            rateLimiter(limit = 3, refillPeriod = 5.minutes)
            requestKey {
                it.receive<LoginRequest>().email
            }
        }
    }

    install(Sessions) {
        cookie<UserSession>("user-session") {
            cookie.sameSite = "strict"
            cookie.secure = true
            cookie.httpOnly = true
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when(cause) {
                is BadRequestException -> {
                    call.respondText(text = "400: $cause", status = HttpStatusCode.BadRequest)
                }
                is RequestValidationException -> {
                    call.respondText(contentType = ContentType.Application.Json, text = cause.reasons.firstOrNull() ?: "", status = HttpStatusCode.BadRequest)
                }
                else -> call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
            }
        }
    }
}
