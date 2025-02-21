package de.lambda9.ready2race.backend.app.appuser.boundary

import de.lambda9.ready2race.backend.afterNow
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.appuser.control.*
import de.lambda9.ready2race.backend.app.appuser.entity.*
import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.email.entity.EmailPriority
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplatePlaceholder
import de.lambda9.ready2race.backend.app.appuser.entity.PasswordResetInitRequest
import de.lambda9.ready2race.backend.app.role.boundary.RoleService
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.ready2race.backend.security.RandomUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.*
import kotlin.time.Duration.Companion.days

object AppUserService {

    private val registrationLifeTime = 1.days
    private val invitationLifeTime = 7.days
    private val passwordResetLifeTime = 1.days

    fun get(
        id: UUID,
    ): App<AppUserError, ApiResponse.Dto<AppUserDto>> =
        AppUserRepo.getWithRoles(id).orDie().onNullFail { AppUserError.NotFound }.andThen {
            it.appUserDto()
        }.map {
            ApiResponse.Dto(it)
        }

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

    fun pageInvitations(
        params: PaginationParameters<AppUserInvitationWithRolesSort>,
    ): App<Nothing, ApiResponse.Page<AppUserInvitationDto, AppUserInvitationWithRolesSort>> = KIO.comprehension {
        val total = !AppUserInvitationRepo.countWithRoles(params.search).orDie()
        val page = !AppUserInvitationRepo.pageWithRoles(params).orDie()

        page.forEachM { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun pageRegistrations(
        params: PaginationParameters<AppUserRegistrationSort>,
    ): App<Nothing, ApiResponse.Page<AppUserRegistrationDto, AppUserRegistrationSort>> = KIO.comprehension {
        val total = !AppUserRegistrationRepo.count(params.search).orDie()
        val page = !AppUserRegistrationRepo.page(params).orDie()

        page.forEachM { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun invite(
        request: InviteRequest,
        inviter: AppUserWithPrivilegesRecord,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !EmailAddressRepo.exists(request.email).orDie()
            .onTrueFail { AppUserError.EmailAlreadyInUse }

        val record = !request.toRecord(inviter.id!!, invitationLifeTime)
        val id = !AppUserInvitationRepo.create(record).orDie()

        !RoleService.checkAssignable(request.roles)
        !AppUserInvitationHasRoleRepo.create(
            request.roles.map {
                AppUserInvitationHasRoleRecord(
                    appUserInvitation = id,
                    role = it
                )
            }
        ).orDie()

        val content = !EmailService.getTemplate(
            EmailTemplateKey.USER_INVITATION,
            request.language,
        ).map { template ->
            template.toContent(
                EmailTemplatePlaceholder.RECIPIENT to request.firstname + " " + request.lastname,
                EmailTemplatePlaceholder.SENDER to inviter.firstname + " " + inviter.lastname,
                EmailTemplatePlaceholder.LINK to request.callbackUrl + record.token,
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
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        val invitation = !AppUserInvitationRepo.consumeWithRoles(request.token).orDie()
            .onNullFail { AppUserError.InvitationNotFound }

        val record = !invitation.toAppUser(request.password)
        val id = !AppUserRepo.create(record).orDie()

        val roles = invitation.roles!!.map { it!!.id!! }

        !RoleService.checkAssignable(roles)
        !AppUserHasRoleRepo.create(
            roles.map {
                AppUserHasRoleRecord(
                    appUser = id,
                    role = it
                )
            }
        ).orDie()

        KIO.ok(
            ApiResponse.Created(id)
        )
    }

    // Error-Code 409 "Conflict" is reserved by the "Email already in use" error. Should another 409 be created, the Error Display in the Frontend-Application needs to be updated
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


    // Error-Codes 404 and 409 are reserved by the Captcha errors. Should another 404 or 409 be created, the Error Display in the Frontend-Application needs to be updated
    fun initPasswordReset(
        request: PasswordResetInitRequest,
    ): App<Nothing, ApiResponse.NoData> = KIO.comprehension {
        val appUser = !AppUserRepo.getByEmail(request.email).orDie()

        if (appUser != null) {
            !AppUserPasswordResetRepo.delete(appUser.id).orDie()

            val token = !AppUserPasswordResetRepo.create(
                AppUserPasswordResetRecord(
                    token = RandomUtilities.token(),
                    appUser = appUser.id,
                    expiresAt = passwordResetLifeTime.afterNow(),
                )
            ).orDie()

            val content = !EmailService.getTemplate(
                EmailTemplateKey.USER_RESET_PASSWORD,
                request.language,
            ).map { template ->
                template.toContent(
                    EmailTemplatePlaceholder.RECIPIENT to appUser.firstname + " " + appUser.lastname,
                    EmailTemplatePlaceholder.LINK to request.callbackUrl + token
                )
            }

            !EmailService.enqueue(
                recipient = request.email,
                content = content,
                // todo: Email Priority = high?
            )
        }
        noData
    }

    fun resetPassword(
        token: String,
        request: PasswordResetRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val passwordReset =
            !AppUserPasswordResetRepo.consume(token).orDie().onNullFail { AppUserError.PasswordResetNotFound }

        val newPassword = !PasswordUtilities.hash(request.password)
        !AppUserRepo.update(passwordReset.appUser) { password = newPassword }.orDie()

        noData
    }


    fun deleteExpiredInvitations(): App<Nothing, Int> =
        AppUserInvitationRepo.deleteExpired().orDie()

    fun deleteExpiredRegistrations(): App<Nothing, Int> =
        AppUserRegistrationRepo.deleteExpired().orDie()

    fun deleteExpiredPasswordResets(): App<Nothing, Int> =
        AppUserPasswordResetRepo.deleteExpired().orDie()
}