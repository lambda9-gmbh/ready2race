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
import java.io.ByteArrayOutputStream
import java.io.PrintWriter

val logger = KotlinLogging.logger {}

suspend fun ApplicationCall.respondKIO(
    f: ApplicationCall.() -> KIO<JEnv, ToApiError, ApiResponse>,
) {
    val exit = f().transact().unsafeRunSync(kioEnv)
    exit.fold(
        onError = { error ->
            logger.debug { "respondKIO{error=${error}}" }
            val apiError = error.respond()
            apiError.headers.forEach { entry ->
                response.headers.append(entry.key, entry.value)
            }
            respond(apiError.status, mapOf(
                "message" to apiError.message,
                "details" to apiError.details,
            )
            )
        },
        onDefect = { defect ->
            logger.error(defect) { "An internal error occurred" }
            val details = when {
                kioEnv.env.config.mode != Config.Mode.PROD -> {
                    try {
                        val out = ByteArrayOutputStream()
                        val writer = PrintWriter(out)
                        defect.printStackTrace(writer)
                        mapOf("error" to out.toString(Charsets.UTF_8))
                    } catch (t: Throwable) {
                        // Ignore error since this is optional anyway.
                        emptyMap<String, Any>()
                    }
                }

                else ->
                    emptyMap()
            }

            respond(
                HttpStatusCode.InternalServerError, mapOf(
                "message" to "Ein technischer Fehler ist aufgetreten. Bitte kommen Sie später wieder.",
                "details" to details,
            )
            )
        },
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