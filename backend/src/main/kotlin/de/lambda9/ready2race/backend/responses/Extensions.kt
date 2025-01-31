package de.lambda9.ready2race.backend.responses

import de.lambda9.ready2race.backend.Config
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.plugins.kioEnv
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.fold
import de.lambda9.tailwind.jooq.transact
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.io.PrintWriter
import java.io.StringWriter

private val logger = KotlinLogging.logger {}

suspend fun ApplicationCall.respondError(
    error: ToApiError,
) {
    val apiError = error.respond()
    apiError.headers.forEach { entry ->
        response.headers.append(entry.key, entry.value)
    }
    respond(apiError.status, apiError)
}

suspend fun ApplicationCall.respondDefect(
    defect: Throwable,
) {
    logger.error(defect) { "An internal error occurred" }
    val details = when {
        kioEnv.env.config.mode != Config.Mode.PROD -> {
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                defect.printStackTrace(pw)
                mapOf("error" to sw.toString())
            } catch (t: Throwable) {
                null
            }
        }

        else ->
            null
    }

    val apiError = ApiError(
        status = HttpStatusCode.InternalServerError,
        message = "An unexpected error has occurred.",
        details = details
    )

    respond(
        HttpStatusCode.InternalServerError, mapOf(
            "message" to "An unexpected error has occurred.",
            "details" to details,
        )
    )
}

suspend fun ApplicationCall.respondKIO(
    f: ApplicationCall.() -> KIO<JEnv, ToApiError, ApiResponse>,
) {
    val exit = f().transact().unsafeRunSync(kioEnv)
    exit.fold(
        onError = { respondError(it) },
        onDefect = { respondDefect(it) },
        onSuccess = { apiResponse ->
            response.status(apiResponse.status)
            when (apiResponse) {
                ApiResponse.NoData -> { }
                is ApiResponse.Dto<*> -> {
                    respond(apiResponse.dto)
                }
                is ApiResponse.Page<*,*> -> {
                    respond(apiResponse)
                }
                is ApiResponse.File -> {
                    respondBytes(apiResponse.bytes, contentType = apiResponse.contentType)
                }
                is ApiResponse.Created -> {
                    respondText(text = apiResponse.id.toString(), status = apiResponse.status)
                }
            }
        }
    )
}