package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.requests.RequestError
import de.lambda9.ready2race.backend.responses.ToApiError
import de.lambda9.ready2race.backend.responses.respondDefect
import de.lambda9.ready2race.backend.responses.respondError
import de.lambda9.tailwind.core.KIOException
import io.ktor.server.application.*
import io.ktor.server.plugins.*
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

                is BadRequestException -> {
                    call.respondError(RequestError.Other(cause))
                }

                else -> {
                    call.respondDefect(cause)
                }
            }
        }
    }
}