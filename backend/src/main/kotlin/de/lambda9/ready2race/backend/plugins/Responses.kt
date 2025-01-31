package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.requests.RequestError
import de.lambda9.ready2race.backend.responses.respondDefect
import de.lambda9.ready2race.backend.responses.respondError
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*

fun Application.configureResponses() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when(cause) {
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