package de.lambda9.ready2race.backend.http

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.boundary.AuthService
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.auth.entity.PrivilegeScope
import de.lambda9.ready2race.backend.app.validatePrivilege
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.serialization.jsonMapper
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.sessions.*

sealed interface RequestError: ServiceError {

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

fun ApplicationCall.authenticate(
    privilege: Privilege,
    thenDo:  KIO.ComprehensionScope<JEnv, ServiceError>.(AppUserWithPrivilegesRecord, PrivilegeScope) -> App<ServiceError, ApiResponse>
): App<ServiceError, ApiResponse> = KIO.comprehension {

    val userSession = sessions.get<UserSession>()
    val user = !AuthService.getAppUserByToken(userSession?.token)
    val privilegeScope = !user.validatePrivilege(privilege)

    thenDo(user, privilegeScope)
}

fun ApplicationCall.authenticate(
    thenDo: KIO.ComprehensionScope<JEnv, ServiceError>.(AppUserWithPrivilegesRecord) -> App<ServiceError, ApiResponse>
): App<ServiceError, ApiResponse> = KIO.comprehension {

    val userSession = sessions.get<UserSession>()
    val user = !AuthService.getAppUserByToken(userSession?.token)

    thenDo(user)
}

fun <T> ApplicationCall.pathParam(
    key: String,
    f: (String) -> T,
): App<ServiceError, T> = KIO.effect {
    parameters[key]?.let(f)
}.mapError { RequestError.ParameterUnparsable(key) }.onNullFail { RequestError.MissingRequiredParameter(key) }

fun <T> ApplicationCall.optionalQueryParam(
    key: String,
    f: (String) -> T,
): App<ServiceError, T?> = KIO.effect {
    request.queryParameters[key]?.let(f)
}.mapError { RequestError.ParameterUnparsable(key) }

fun <T> ApplicationCall.queryParam(
    key: String,
    f: (String) -> T,
): App<ServiceError, T> =
    optionalQueryParam(key, f).onNullFail { RequestError.MissingRequiredParameter(key) }

inline fun <reified S> ApplicationCall.pagination(): App<ServiceError, PaginationParameters<S>>
    where S : Sortable, S : Enum<S> = KIO.comprehension {

    val limit = !queryParam("limit") { it.toInt() }
    val offset = !queryParam("offset") { it.toInt() }
    val sort = !queryParam("sort") { jsonMapper.readValue<List<Order<S>>>(it) }
    val search = !optionalQueryParam("search") { it }

    if (limit < 1 || offset < 0) return@comprehension KIO.fail(RequestError.InvalidPagination)

    KIO.ok(
        PaginationParameters(
            limit, offset, sort, search
        )
    )
}