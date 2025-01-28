package de.lambda9.ready2race.backend.plugins

import de.lambda9.ready2race.backend.requests.ExtendedBadRequestException
import de.lambda9.ready2race.backend.responses.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureResponses() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when(cause) {
                is ExtendedBadRequestException -> {
                    call.respondError(cause.requestError)
                }
                is BadRequestException -> {
                    call.respondText(text = "400: Could not parse payload to expected Type. Please make sure, no required fields are missing in your request body.", status = HttpStatusCode.BadRequest)
                }
                is RequestValidationException -> {
                    call.respondText(contentType = ContentType.Application.Json, text = cause.reasons.firstOrNull() ?: "", status = HttpStatusCode.BadRequest)
                }
                else -> call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
            }
        }
    }
}