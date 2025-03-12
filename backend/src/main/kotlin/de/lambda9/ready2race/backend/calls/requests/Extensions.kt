package de.lambda9.ready2race.backend.calls.requests

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.boundary.AuthService
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.captcha.boundary.CaptchaService
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.calls.pagination.Order
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.pagination.Sortable
import de.lambda9.ready2race.backend.calls.responses.ToApiError
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.sessions.UserSession
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validators.IntValidators
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*
import java.util.*

val logger = KotlinLogging.logger {}

suspend inline fun <reified V : Validatable> ApplicationCall.receiveNullableKIO(example: V): IO<RequestError, V?> =
    try {
        val payload = receiveNullable<V>()
        val result = payload?.validate()
        if (result is ValidationResult.Invalid) {
            KIO.fail(RequestError.BodyValidationFailed(result))
        } else {
            KIO.ok(payload)
        }
    } catch (ex: Exception) {
        logger.error(ex) { "Error during receiving body" }
        KIO.fail(RequestError.Other(ex))
    }

suspend inline fun <reified V : Validatable> ApplicationCall.receiveKIO(example: V): IO<RequestError, V> =
    receiveNullableKIO(example).onNullFail { RequestError.BodyMissing(example) }

fun ApplicationCall.authenticate(
    privilege: Privilege,
): App<AuthError, AppUserWithPrivilegesRecord> =
    AuthService.useSessionToken(sessions.get<UserSession>()?.token).failIf(
        condition = { user ->
            user.privileges!!
                .none {
                    it!!.action == privilege.action.name
                        && it.resource == privilege.resource.name
                        && Privilege.Scope.valueOf(it.scope).level >= privilege.scope.level
                }
        },
        transform = { AuthError.PrivilegeMissing },
    )

fun ApplicationCall.authenticate(
    action: Privilege.Action,
    resource: Privilege.Resource,
): App<AuthError, Pair<AppUserWithPrivilegesRecord, Privilege.Scope>> =
    AuthService.useSessionToken(sessions.get<UserSession>()?.token).map { user ->
        user.privileges!!
            .filter { it!!.action == action.name && it.resource == resource.name }
            .map { Privilege.Scope.valueOf(it!!.scope) }
            .maxByOrNull { it.level }
            ?.let { user to it }
    }.onNullFail { AuthError.PrivilegeMissing }

fun ApplicationCall.authenticate(): App<AuthError, AppUserWithPrivilegesRecord> = KIO.comprehension {

    val userSession = sessions.get<UserSession>()
    AuthService.useSessionToken(userSession?.token)
}

fun <T> ApplicationCall.pathParam(
    key: String,
    f: (String) -> T,
): App<RequestError, T> = KIO.effect {
    parameters[key]!!.let(f)
}.mapError { RequestError.ParameterUnparsable(key) }

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

    val limit = !optionalQueryParam("limit") { it.toInt() }
    val offset = !optionalQueryParam("offset") { it.toInt() }
    val sort = !optionalQueryParam("sort") { jsonMapper.readValue<List<Order<S>>>(it) }
    val search = !optionalQueryParam("search")

    ValidationResult.allOf(
        IntValidators.notNegative(offset),
        IntValidators.min(1)(limit)
    ).fold(
        onValid = { KIO.ok(PaginationParameters(limit, offset, sort, search)) },
        onInvalid = { KIO.fail(RequestError.InvalidPagination(it)) }
    )
}

fun ApplicationCall.checkCaptcha(): App<ToApiError, Unit> = KIO.comprehension {
    val captchaId = !queryParam("challenge") { UUID.fromString(it) }
    val captchaInput = !queryParam("input") { it.toInt() }
    CaptchaService.trySolution(captchaId, captchaInput)
}