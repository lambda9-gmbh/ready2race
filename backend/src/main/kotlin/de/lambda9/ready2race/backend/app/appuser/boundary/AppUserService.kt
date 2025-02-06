package de.lambda9.ready2race.backend.app.appuser.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.appuser.control.*
import de.lambda9.ready2race.backend.app.appuser.entity.*
import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplatePlaceholder
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import kotlin.time.Duration.Companion.days

object AppUserService {

    private val registrationTokenLifeTime = 1.days

    fun page(
        params: PaginationParameters<AppUserWithRolesSort>,
    ): App<Nothing, ApiResponse.Page<AppUserDto, AppUserWithRolesSort>> = KIO.comprehension {
        val total = !AppUserRepo.countWithRoles(params.search).orDie()
        val page = !AppUserRepo.pageWithRoles(params).orDie()

        page.forEachM { it.appUserDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun register(
        request: RegisterRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !AppUserRepo.existsByEmail(request.email).orDie()
            .onTrueFail { AppUserError.EmailAlreadyInUse }
        !AppUserRegistrationRepo.existsByEmail(request.email).orDie()
            .onTrueFail { AppUserError.EmailAlreadyInUse }

        val record = !request.toRecord()
        val token = !AppUserRegistrationRepo.create(record).orDie()

        val content = !EmailService.getTemplate(
            EmailTemplateKey.VERIFY_REGISTRATION,
            request.language
        ).map { template ->
            template.toContent(
                EmailTemplatePlaceholder.RECIPIENT to request.firstname + " " + request.lastname,
                EmailTemplatePlaceholder.LINK to request.callbackUrl + token
            )
        }

        !EmailService.enqueue(
            recipient = request.email,
            content = content,
        )

        noData
    }

    fun verifyRegistration(
        request: VerifyRegistrationRequest
    ): App<AppUserError, ApiResponse.Created> = KIO.comprehension {

        val registration = !AppUserRegistrationRepo.consume(request.token, registrationTokenLifeTime).orDie()
            .onNullFail { AppUserError.EmailAlreadyInUse }

        val record = !registration.toAppUser()
        AppUserRepo.create(record).orDie().map {
            ApiResponse.Created(it)
        }
    }

    fun deleteExpiredRegistrations(): App<Nothing, Int> =
        AppUserRegistrationRepo.deleteExpired(registrationTokenLifeTime).orDie()
}