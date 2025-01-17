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
import de.lambda9.ready2race.backend.plugins.logger
import de.lambda9.ready2race.backend.serialization.jsonMapper
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.catchError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.sessions.*
import java.util.*

sealed interface RequestError : ServiceError {

    data class MissingRequiredQueryParameter(val key: String) : RequestError
    data class ParameterUnparsable(val key: String) : RequestError
    data object InvalidPagination : RequestError

    override fun respond(): ApiError =
        ApiError(
            status = HttpStatusCode.BadRequest,
            message = when (this) {
                is MissingRequiredQueryParameter -> "Missing required query parameter $key"
                is ParameterUnparsable -> "Query parameter $key could not be parsed"
                InvalidPagination -> "Invalid pagination parameters (limit must be bigger than '0', offset must not be negative)"
            }
        )
}

fun ApplicationCall.authenticate(
    privilege: Privilege,
    thenDo: KIO.ComprehensionScope<JEnv, ServiceError>.(AppUserWithPrivilegesRecord, PrivilegeScope) -> App<ServiceError, ApiResponse>
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

fun ApplicationCall.pathParam(
    key: String,
): App<ServiceError, UUID> = KIO.comprehension {
    val param = parameters[key]!!
    try {
        KIO.ok(UUID.fromString(param))
    } catch (e: Exception) {
        KIO.fail(RequestError.ParameterUnparsable(key))
    }
}

inline fun <reified T> ApplicationCall.queryParam(
    key: String,
): App<ServiceError, T> = KIO.comprehension {
    val param = request.queryParameters[key]
        ?: return@comprehension KIO.fail(RequestError.MissingRequiredQueryParameter(key))
    try {
        KIO.ok(jsonMapper.readValue<T>(param))
    } catch (e: Exception) {
        KIO.fail(RequestError.ParameterUnparsable(key))
    }
}

inline fun <reified T> ApplicationCall.optionalQueryParam(
    key: String,
): App<Nothing, T?> = queryParam<T>(key).catchError { App.ok(null) }

inline fun <reified T> ApplicationCall.queryParamList(
    key: String,
): App<ServiceError, List<T>> = KIO.comprehension {
    val params = request.queryParameters.getAll(key) ?: emptyList()
    try {
        KIO.ok(params.map {
            jsonMapper.readValue<T>(it)
        })
    } catch (e: Exception) {
        KIO.fail(RequestError.ParameterUnparsable(key))
    }
}

inline fun <reified S> ApplicationCall.pagination(): App<ServiceError, PaginationParameters<S>>
    where S : Sortable, S : Enum<S> = KIO.comprehension {

    val limit = !queryParam<Int>("limit")
    val offset = !queryParam<Int>("offset")
    val sort = !queryParam<List<Order<S>>>("sort")
    val search = !optionalQueryParam<String>("search")

    if (limit < 1 || offset < 0) return@comprehension KIO.fail(RequestError.InvalidPagination)

    KIO.ok(
        PaginationParameters(
            limit, offset, sort, search
        )
    )
}