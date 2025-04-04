package de.lambda9.ready2race.backend.app.auth.boundary

import de.lambda9.ready2race.backend.afterNow
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.auth.control.*
import de.lambda9.ready2race.backend.app.auth.entity.*
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.recoverDefault
import de.lambda9.tailwind.core.extensions.kio.traverse
import kotlin.time.Duration.Companion.minutes

object AuthService {

    private val tokenLifetime = 30.minutes

    fun getAllPrivileges(): App<Nothing, ApiResponse.Dto<List<PrivilegeDto>>> = KIO.comprehension {
        val records = !PrivilegeRepo.all().orDie()
        records.traverse { it.toPrivilegeDto() }.map {
            ApiResponse.Dto(
                it
            )
        }
    }

    fun login(
        request: LoginRequest,
        onSuccess: (String) -> Unit,
    ): App<AuthError, ApiResponse.Dto<LoginDto>> = KIO.comprehension {

        val user = !AppUserRepo.getWithPrivilegesByEmail(request.email).orDie().onNullFail { AuthError.CredentialsIncorrect }

        val credentialsOk = !PasswordUtilities.check(request.password, user.password!!)

        if (credentialsOk) {

            val record = !user.toSession(tokenLifetime)
            val token = !AppUserSessionRepo.create(record).orDie()
            onSuccess(token)

            user.loginDto().map {
                ApiResponse.Dto(it)
            }
        } else {
            KIO.fail(AuthError.CredentialsIncorrect)
        }
    }

    fun checkLogin(
        token: String?,
    ): App<Nothing, ApiResponse> = KIO.comprehension {

        val user = !useSessionToken(token).recoverDefault { null }

        user?.loginDto()?.map { ApiResponse.Dto(it) } ?: noData
    }

    fun logout(
        token: String?,
        onSuccess: () -> Unit,
    ): App<Nothing, ApiResponse.NoData> = KIO.comprehension {

        !AppUserSessionRepo.delete(token).orDie()
        onSuccess()

        noData
    }

    fun useSessionToken(
        token: String?
    ): App<AuthError, AppUserWithPrivilegesRecord> = KIO.comprehension {

        if (token == null) {
            return@comprehension KIO.fail(AuthError.TokenInvalid)
        }

        val valid = !AppUserSessionRepo.update(token) {
            expiresAt = tokenLifetime.afterNow()
        }.orDie().onNullFail { AuthError.TokenInvalid }

        AppUserRepo.getWithPrivileges(valid.appUser).orDie().onNullFail { AuthError.TokenInvalid }
    }

    fun deleteExpiredTokens(): App<Nothing, Int> =
        AppUserSessionRepo.deleteExpired().orDie()
}