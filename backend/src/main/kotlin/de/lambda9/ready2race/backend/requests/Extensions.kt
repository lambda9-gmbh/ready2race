package de.lambda9.ready2race.backend.requests

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.boundary.AuthService
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.validatePrivilege
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.kio.toKio
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.pagination.Sort
import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.plugins.requests.validation.ValidatableValidationException
import de.lambda9.ready2race.backend.serialization.jsonMapper
import de.lambda9.ready2race.backend.sessions.UserSession
import de.lambda9.ready2race.backend.validation.StructuredValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validators.IntValidators
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*

suspend inline fun <reified V: Validatable> ApplicationCall.receiveV(example: V): IO<RequestError, V> =
    runCatching { receive<V>() }.toKio().mapError {
        when (it) {
            is BadRequestException -> RequestError.BodyUnparsable(example)
            is ValidatableValidationException -> RequestError.BodyValidationFailed(it.reason)
            else -> RequestError.Other(it)
        }
    }

fun ApplicationCall.authenticate(
    action: Privilege.Action,
    resource: Privilege.Resource,
): App<AuthError, Pair<AppUserWithPrivilegesRecord, Privilege.Scope>> = KIO.comprehension {

    val userSession = sessions.get<UserSession>()
    val user = !AuthService.getAppUserByToken(userSession?.token)
    user.validatePrivilege(action, resource).map {
        user to it
    }
}

fun ApplicationCall.authenticate(): App<AuthError, AppUserWithPrivilegesRecord> = KIO.comprehension {

    val userSession = sessions.get<UserSession>()
    AuthService.getAppUserByToken(userSession?.token)
}

fun <T> ApplicationCall.pathParam(
    key: String,
    f: (String) -> T,
): App<RequestError, T> = KIO.effect {
    parameters[key]?.let(f)
}.mapError { RequestError.ParameterUnparsable(key) }.onNullFail { RequestError.PathParameterUnknown(key) }

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
    optionalQueryParam(key, f).onNullFail { RequestError.RequiredQueryParameterMissing(key) }

fun ApplicationCall.queryParam(
    key: String,
): App<RequestError, String> = queryParam(key) { it }

inline fun <reified S> ApplicationCall.pagination(): App<RequestError, PaginationParameters<S>>
    where S : Sortable, S : Enum<S> = KIO.comprehension {

    val limit = !queryParam("limit") { it.toInt() }
    val offset = !queryParam("offset") { it.toInt() }
    val sort = !queryParam("sort") { jsonMapper.readValue<Sort<S>>(it) }
    val search = !optionalQueryParam("search")

    StructuredValidationResult.allOf(
        IntValidators.notNegative(offset),
        IntValidators.min(1)(limit)
    ).fold(
        onValid = { KIO.ok(PaginationParameters(limit, offset, sort, search)) },
        onInvalid = { KIO.fail(RequestError.InvalidPagination(it)) }
    )
}