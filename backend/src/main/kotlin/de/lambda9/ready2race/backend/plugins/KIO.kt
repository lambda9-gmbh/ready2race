package de.lambda9.ready2race.backend.plugins

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.Config
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.auth.boundary.AuthService
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.validatePrivilege
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.http.*
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.fold
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.jooq.transact
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import java.io.ByteArrayOutputStream
import java.io.PrintWriter

sealed interface RequestError: ToApiError {

    data class MissingRequiredParameter(val key: String): RequestError
    data class ParameterUnparsable(val key: String): RequestError
    data object InvalidPagination: RequestError

    override fun respond(): ApiError =
        ApiError(
            status = HttpStatusCode.BadRequest,
            message = when (this) {
                is MissingRequiredParameter -> "Missing required query parameter $key"
                is ParameterUnparsable -> "Query parameter $key could not be parsed"
                InvalidPagination -> "Invalid pagination parameters (limit must be bigger than '0', offset must not be negative)"
            }
        )
}

private val kioEnvKey = AttributeKey<JEnv>("kioEnv")

val logger = KotlinLogging.logger {}

val ApplicationCall.kioEnv get(): JEnv =
    application.attributes[kioEnvKey]

fun Application.configureKIO(env: JEnv) {
    attributes.put(kioEnvKey, env)
}

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
            ))
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

            respond(HttpStatusCode.InternalServerError, mapOf(
                "message" to "Ein technischer Fehler ist aufgetreten. Bitte kommen Sie später wieder.",
                "details" to details,
            ))
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

fun ApplicationCall.authenticate(
    action: Privilege.Action,
    resource: Privilege.Resource,
): App<AuthService.AuthError, Pair<AppUserWithPrivilegesRecord, Privilege.Scope>> = KIO.comprehension {

    val userSession = sessions.get<UserSession>()
    val user = !AuthService.getAppUserByToken(userSession?.token)
    user.validatePrivilege(action, resource).map {
        user to it
    }
}

fun ApplicationCall.authenticate(): App<AuthService.AuthError, AppUserWithPrivilegesRecord> = KIO.comprehension {

    val userSession = sessions.get<UserSession>()
    AuthService.getAppUserByToken(userSession?.token)
}

fun <T> ApplicationCall.pathParam(
    key: String,
    f: (String) -> T,
): App<RequestError, T> = KIO.effect {
    parameters[key]?.let(f)
}.mapError { RequestError.ParameterUnparsable(key) }.onNullFail { RequestError.MissingRequiredParameter(key) }

fun ApplicationCall.pathParam(
    key: String,
): App<RequestError, String> = pathParam(key) { it }

fun <T> ApplicationCall.optionalQueryParam(
    key: String,
    f: (String) -> T,
): App<RequestError, T?> = KIO.effect {
    request.queryParameters[key]?.let(f)
}.mapError { RequestError.ParameterUnparsable(key) }

fun ApplicationCall.optionalQueryParam(
    key: String,
): App<RequestError, String?> = optionalQueryParam(key) { it }

fun <T> ApplicationCall.queryParam(
    key: String,
    f: (String) -> T,
): App<RequestError, T> =
    optionalQueryParam(key, f).onNullFail { RequestError.MissingRequiredParameter(key) }

fun ApplicationCall.queryParam(
    key: String,
): App<RequestError, String> = queryParam(key) { it }

inline fun <reified S> ApplicationCall.pagination(): App<RequestError, PaginationParameters<S>>
    where S : Sortable, S : Enum<S> = KIO.comprehension {

    val limit = !queryParam("limit") { it.toInt() }
    val offset = !queryParam("offset") { it.toInt() }
    val sort = !queryParam("sort") { jsonMapper.readValue<List<Order<S>>>(it) }
    val search = !optionalQueryParam("search")

    if (limit < 1 || offset < 0) return@comprehension KIO.fail(RequestError.InvalidPagination)

    KIO.ok(
        PaginationParameters(
            limit, offset, sort, search
        )
    )
}