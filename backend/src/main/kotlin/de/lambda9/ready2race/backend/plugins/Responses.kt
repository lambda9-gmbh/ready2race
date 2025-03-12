package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.calls.requests.RequestError
import de.lambda9.ready2race.backend.calls.responses.ToApiError
import de.lambda9.ready2race.backend.calls.responses.respondDefect
import de.lambda9.ready2race.backend.calls.responses.respondError
import de.lambda9.tailwind.core.KIOException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*

fun Application.configureResponses() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is KIOException -> {
                    cause.error.fold(
                        onExpected = {
                            if (it is ToApiError) {
                                call.respondError(it)
                            } else {
                                call.respondDefect(cause)
                            }
                        },
                        onPanic = { call.respondDefect(it) }
                    )
                }

                else -> {
                    call.respondDefect(cause)
                }
            }
        }
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            val retryAfter = call.response.headers["Retry-After"]
            call.respondError(RequestError.TooManyRequests(retryAfter))
        }
    }
}