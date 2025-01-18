package de.lambda9.ready2race.backend.app.auth.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.control.AppUserSessionRepo
import de.lambda9.ready2race.backend.app.auth.control.loginDto
import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.app.auth.entity.LoginDto
import de.lambda9.ready2race.backend.app.user.control.AppUserRepo
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.http.ApiError
import de.lambda9.ready2race.backend.http.ApiResponse
import de.lambda9.ready2race.backend.http.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.ready2race.backend.security.RandomUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.catchError
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.ktor.http.*
import kotlin.time.Duration.Companion.minutes

object AuthService {

    private const val TOKEN_LENGTH = 30
    private val tokenLifetime = 30.minutes

    enum class AuthError: ServiceError {

        CredentialsIncorrect,
        TokenInvalid,
        PrivilegeMissing;

        override fun respond(): ApiError = when (this) {
            CredentialsIncorrect -> ApiError(status = HttpStatusCode.Unauthorized, message = "Incorrect credentials")
            TokenInvalid -> ApiError(status = HttpStatusCode.Unauthorized, message = "Invalid session token")
            PrivilegeMissing -> ApiError(status = HttpStatusCode.Unauthorized, message = "Missing privilege")
        }
    }

    fun login(
        request: LoginRequest,
        onSuccess: (String) -> Unit
    ): App<AuthError, ApiResponse.Dto<LoginDto>> = KIO.comprehension {

        val user = !AppUserRepo.getWithPrivilegesByEmail(request.email).orDie().onNullFail { AuthError.CredentialsIncorrect }

        val credentialsOk = !PasswordUtilities.check(request.password, user.password!!)

        if (credentialsOk) {

            val token = RandomUtilities.alphanumerical(TOKEN_LENGTH)

            !AppUserSessionRepo.create(user.id!!, token).orDie()
            onSuccess(token)

            user.loginDto().map {
                ApiResponse.Dto(it)
            }
        } else {
            KIO.fail(AuthError.CredentialsIncorrect)
        }
    }

    fun checkLogin(
        token: String?
    ): App<AuthError, ApiResponse> = KIO.comprehension {

        val user = !getAppUserByToken(token).catchError { KIO.ok(null) }

        user?.loginDto()?.map { ApiResponse.Dto(it) } ?: noData
    }

    fun logout(
        token: String?,
        onSuccess: () -> Unit
    ): App<AuthError, ApiResponse.NoData> = KIO.comprehension {

        !AppUserSessionRepo.delete(token).orDie()
        onSuccess()
        noData
    }

    fun getAppUserByToken(
        token: String?
    ): App<AuthError, AppUserWithPrivilegesRecord> = KIO.comprehension {

        if (token == null) {
            return@comprehension KIO.fail(AuthError.TokenInvalid)
        }

        val valid = !AppUserSessionRepo.useAndGet(token, tokenLifetime).orDie().onNullFail { AuthError.TokenInvalid }

        AppUserRepo.getWithPrivileges(valid.appUser!!).orDie().onNullFail { AuthError.TokenInvalid }
    }
}