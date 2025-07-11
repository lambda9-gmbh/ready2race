package de.lambda9.ready2race.backend.calls.responses

import de.lambda9.ready2race.backend.config.Config
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.calls.comprehension.CallComprehensionScope
import de.lambda9.ready2race.backend.calls.comprehension.comprehension
import de.lambda9.ready2race.backend.plugins.kioEnv
import de.lambda9.tailwind.core.Cause
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.fold
import de.lambda9.tailwind.jooq.transact
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import jakarta.activation.MimetypesFileTypeMap
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLConnection

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

suspend fun ApplicationCall.respondCause(
    cause: Cause<ToApiError>,
) = cause.fold(
    onExpected = { respondError(it) },
    onPanic = { respondDefect(it) },
)

suspend fun ApplicationCall.respondKIO(
    app: KIO<JEnv, ToApiError, ApiResponse>,
) {
    val exit = app.transact().unsafeRunSync(kioEnv)
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

                is ApiResponse.ListDto<*> -> {
                    respond(apiResponse.data)
                }

                is ApiResponse.Page<*, *> -> {
                    respond(apiResponse)
                }

                is ApiResponse.File -> {

                    val contentType = try {
                        ContentType.parse(URLConnection.guessContentTypeFromName(apiResponse.name))
                    } catch(e: BadContentTypeFormatException) {
                        logger.warn(e) { "Could not parse content-type from Document/File ${apiResponse.name}" }
                        ContentType.Application.OctetStream
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

suspend fun ApplicationCall.respondComprehension(
    block: suspend CallComprehensionScope.() -> KIO<JEnv, ToApiError, ApiResponse>
) {
    val app = comprehension(block)
    respondKIO(app)
}