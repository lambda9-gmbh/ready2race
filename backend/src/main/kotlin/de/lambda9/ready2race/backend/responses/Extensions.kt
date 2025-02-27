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
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

private val logger = KotlinLogging.logger {}

suspend fun ApplicationCall.respondError(
    error: ToApiError,
) = respondError(error.respond())

suspend fun ApplicationCall.respondError(
    error: ApiError,
) {
    error.headers.forEach { entry ->
        response.headers.append(entry.key, entry.value)
    }
    respond(error.status, error)
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

    respondError(
        ApiError(
            status = HttpStatusCode.InternalServerError,
            message = "An unexpected error has occurred.",
            details = details
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
            when (apiResponse) {
                ApiResponse.NoData -> {
                    response.status(HttpStatusCode.NoContent)
                }

                is ApiResponse.Dto<*> -> {
                    respond(apiResponse.dto)
                }

                is ApiResponse.Page<*, *> -> {
                    respond(apiResponse)
                }

                is ApiResponse.File -> {
                    val extension = apiResponse.name.substringAfterLast('.').lowercase()
                    val contentType = when (extension) {
                        // todo: extend, if needed
                        "pdf" -> ContentType.Application.Pdf
                        else -> null
                    }
                    response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(
                            ContentDisposition.Parameters.FileName,
                            apiResponse.name
                        ).toString()
                    )
                    respondBytes(apiResponse.bytes, contentType)
                }

                is ApiResponse.Created -> {
                    respondText(apiResponse.id.toString(), status = HttpStatusCode.Created)
                }
            }
        }
    )
}