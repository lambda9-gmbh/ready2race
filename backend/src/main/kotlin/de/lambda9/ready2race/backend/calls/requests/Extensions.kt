package de.lambda9.ready2race.backend.calls.requests

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.boundary.AuthService
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.captcha.boundary.CaptchaService
import de.lambda9.ready2race.backend.pagination.Order
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.calls.responses.ToApiError
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.parsing.Parser
import de.lambda9.ready2race.backend.parsing.Parser.Companion.int
import de.lambda9.ready2race.backend.parsing.Parser.Companion.json
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.ready2race.backend.sessions.UserSession
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validators.IntValidators
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.andThenNotNull
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*
import kotlin.reflect.KClass

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

fun ApplicationCall.authenticateAny(
    vararg privileges: Privilege,
): App<AuthError, AppUserWithPrivilegesRecord> =
    AuthService.useSessionToken(sessions.get<UserSession>()?.token).failIf(
        condition = { user ->
            privileges.none { privilege ->
                user.privileges!!
                    .any {
                        it!!.action == privilege.action.name
                            && it.resource == privilege.resource.name
                            && Privilege.Scope.valueOf(it.scope).level >= privilege.scope.level
                    }
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
    }
        .onNullFail { AuthError.PrivilegeMissing }

fun ApplicationCall.optionalAuthenticate(
    action: Privilege.Action,
    resource: Privilege.Resource,
): App<AuthError, Pair<AppUserWithPrivilegesRecord, Privilege.Scope>?> =
    sessions.get<UserSession>()?.token?.let {token ->
        AuthService.useSessionToken(token).map { user ->
            user.privileges!!
                .filter { it!!.action == action.name && it.resource == resource.name }
                .map { Privilege.Scope.valueOf(it!!.scope) }
                .maxByOrNull { it.level }
                ?.let { user to it }
        }
    }?: KIO.ok(null)

fun ApplicationCall.authenticate(): App<AuthError, AppUserWithPrivilegesRecord> = KIO.comprehension {

    val userSession = sessions.get<UserSession>()
    AuthService.useSessionToken(userSession?.token)
}

fun <T: Any> Parser<T>.param(key: String, input: String, kClass: KClass<T>): IO<RequestError, T> =
    invoke(input) { task ->
        task.mapError { RequestError.ParameterUnparsable(key, input, kClass) }
    }

inline fun <reified T : Any> ApplicationCall.pathParam(
    key: String,
    parser: Parser<T>,
): IO<RequestError, T> =
    KIO.ok(parameters[key])
        .onNullFail { RequestError.PathParameterMissing(key) }
        .andThen { parser.param(key, it, T::class) }

fun ApplicationCall.pathParam(
    key: String,
): IO<RequestError, String> = pathParam(key) { it }

inline fun <reified T : Any> ApplicationCall.optionalQueryParam(
    key: String,
    parser: Parser<T>,
): IO<RequestError, T?> =
    KIO.ok(request.queryParameters[key]).andThenNotNull { parser.param(key, it, T::class) }

fun ApplicationCall.optionalQueryParam(
    key: String,
): IO<RequestError, String?> = optionalQueryParam(key) { it }

inline fun <reified T : Any> ApplicationCall.queryParam(
    key: String,
    parser: Parser<T>,
): IO<RequestError, T> =
    optionalQueryParam(key, parser).onNullFail { RequestError.RequiredQueryParameterMissing(key) }

fun ApplicationCall.queryParam(
    key: String,
): IO<RequestError, String> = queryParam(key) { it }

inline fun <reified S> ApplicationCall.pagination(): IO<RequestError, PaginationParameters<S>>
    where S : Sortable, S : Enum<S> = KIO.comprehension {

    val limit = !optionalQueryParam("limit", int)
    val offset = !optionalQueryParam("offset", int)
    val sort = !optionalQueryParam("sort", json<List<Order<S>>>())
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
    val captchaId = !queryParam("challenge", uuid)
    val captchaInput = !queryParam("input", int)
    CaptchaService.trySolution(captchaId, captchaInput)
}