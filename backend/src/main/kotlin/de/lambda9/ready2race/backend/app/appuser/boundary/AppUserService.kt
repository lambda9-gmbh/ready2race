package de.lambda9.ready2race.backend.app.appuser.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.control.*
import de.lambda9.ready2race.backend.app.appuser.entity.*
import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.email.entity.EmailPriority
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplatePlaceholder
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.*
import kotlin.time.Duration.Companion.days

object AppUserService {

    private val registrationLifeTime = 1.days
    private val invitationLifeTime = 7.days

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

    fun invite(
        request: InviteRequest,
        inviter: AppUserWithPrivilegesRecord,
    ): App<AppUserError, ApiResponse.NoData> = KIO.comprehension {

        !EmailAddressRepo.exists(request.email).orDie()
            .onTrueFail { AppUserError.EmailAlreadyInUse }

        val record = !request.toRecord(inviter.id!!, invitationLifeTime)
        val token = !AppUserInvitationRepo.create(record).orDie()

        val content = !EmailService.getTemplate(
            EmailTemplateKey.USER_INVITATION,
            request.language,
        ).map { template ->
            template.toContent(
                EmailTemplatePlaceholder.RECIPIENT to request.firstname + " " + request.lastname,
                EmailTemplatePlaceholder.SENDER to inviter.firstname + " " + inviter.lastname,
                EmailTemplatePlaceholder.LINK to request.callbackUrl + token,
            )
        }

        !EmailService.enqueue(
            recipient = request.email,
            content = content,
            priority = EmailPriority.HIGH,
        )

        noData
    }

    fun acceptInvitation(
        request: AcceptInvitationRequest,
    ): App<AppUserError, ApiResponse.Created> = KIO.comprehension {

        val invitation = !AppUserInvitationRepo.consume(request.token).orDie()
            .onNullFail { AppUserError.InvitationNotFound }

        val record = !invitation.toAppUser(request.password)
        AppUserRepo.create(record).orDie().map {
            ApiResponse.Created(it)
        }
    }

    fun register(
        request: RegisterRequest,
    ): App<AppUserError, ApiResponse.NoData> = KIO.comprehension {

        !EmailAddressRepo.exists(request.email).orDie()
            .onTrueFail { AppUserError.EmailAlreadyInUse }

        val record = !request.toRecord(registrationLifeTime)
        val token = !AppUserRegistrationRepo.create(record).orDie()

        val content = !EmailService.getTemplate(
            EmailTemplateKey.USER_REGISTRATION,
            request.language,
        ).map { template ->
            template.toContent(
                EmailTemplatePlaceholder.RECIPIENT to request.firstname + " " + request.lastname,
                EmailTemplatePlaceholder.LINK to request.callbackUrl + token
            )
        }

        !EmailService.enqueue(
            recipient = request.email,
            content = content,
            priority = EmailPriority.HIGH,
        )

        noData
    }

    fun verifyRegistration(
        request: VerifyRegistrationRequest,
    ): App<AppUserError, ApiResponse.Created> = KIO.comprehension {

        val registration = !AppUserRegistrationRepo.consume(request.token).orDie()
            .onNullFail { AppUserError.RegistrationNotFound }

        val record = !registration.toAppUser()
        AppUserRepo.create(record).orDie().map {
            ApiResponse.Created(it)
        }
    }

    fun deleteExpiredRegistrations(): App<Nothing, Int> =
        AppUserRegistrationRepo.deleteExpired().orDie()
}