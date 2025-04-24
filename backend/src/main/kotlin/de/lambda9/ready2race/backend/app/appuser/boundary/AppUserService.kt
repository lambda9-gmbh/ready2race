package de.lambda9.ready2race.backend.app.appuser.boundary

import de.lambda9.ready2race.backend.afterNow
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.appuser.control.*
import de.lambda9.ready2race.backend.app.appuser.entity.*
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.club.control.ClubRepo
import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.email.entity.EmailPriority
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplatePlaceholder
import de.lambda9.ready2race.backend.app.role.boundary.RoleService
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.ADMIN_ROLE
import de.lambda9.ready2race.backend.database.CLUB_REPRESENTATIVE_ROLE
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.USER_ROLE
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.ready2race.backend.security.RandomUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration.Companion.days

object AppUserService {

    private val registrationLifeTime = 1.days
    private val invitationLifeTime = 7.days
    private val passwordResetLifeTime = 1.days

    fun get(
        id: UUID,
    ): App<AppUserError, ApiResponse.Dto<AppUserDto>> = KIO.comprehension {
        val record = !AppUserRepo.getWithRoles(id).orDie().onNullFail { AppUserError.NotFound }
        record.appUserDto().map {
            ApiResponse.Dto(it)
        }
    }

    fun getAllByClubId(
        clubId: UUID,
    ): App<AppUserError, ApiResponse.ListDto<AppUserDto>> = KIO.comprehension {
        val list = !AppUserRepo.getAllByClubIdWithRoles(clubId).orDie()

        list.traverse { it.appUserDto() }.map {
            ApiResponse.ListDto(
                data = it
            )
        }
    }

    fun page(
        params: PaginationParameters<AppUserWithRolesSort>,
    ): App<Nothing, ApiResponse.Page<AppUserDto, AppUserWithRolesSort>> = KIO.comprehension {
        val total = !AppUserRepo.countWithRoles(params.search).orDie()
        val page = !AppUserRepo.pageWithRoles(params).orDie()

        page.traverse { it.appUserDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun update(
        request: UpdateAppUserRequest,
        scope: Privilege.Scope,
        requestingUserId: UUID,
        targetUserId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !KIO.failOn(
            scope == Privilege.Scope.OWN
                && (requestingUserId != targetUserId
                || request.roles.isNotEmpty())
        ) { AuthError.PrivilegeMissing }

        !AppUserRepo.update(targetUserId) {
            firstname = request.firstname
            lastname = request.lastname
            updatedBy = requestingUserId
            updatedAt = LocalDateTime.now()
        }.orDie()
            .onNullFail { AppUserError.NotFound }
            .map { ApiResponse.NoData }

        if (scope == Privilege.Scope.GLOBAL) {
            !RoleService.checkAssignable(request.roles)

            !AppUserHasRoleRepo.deleteExceptSystem(targetUserId).orDie()
            !AppUserHasRoleRepo.create(
                request.roles.map {
                    AppUserHasRoleRecord(
                        appUser = targetUserId,
                        role = it
                    )
                }
            ).orDie()
        }

        noData
    }

    fun pageInvitations(
        params: PaginationParameters<AppUserInvitationWithRolesSort>,
    ): App<Nothing, ApiResponse.Page<AppUserInvitationDto, AppUserInvitationWithRolesSort>> = KIO.comprehension {
        val total = !AppUserInvitationRepo.countWithRoles(params.search).orDie()
        val page = !AppUserInvitationRepo.pageWithRoles(params).orDie()

        page.traverse { it.toDto() }.map {
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

        page.traverse { it.toDto() }.map {
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

        if (request.admin == true) {
            if (inviter.id == SYSTEM_USER) {
                !AppUserInvitationHasRoleRepo.create(
                    AppUserInvitationHasRoleRecord(
                        appUserInvitation = id,
                        role = ADMIN_ROLE,
                    )
                ).orDie()
            } else {
                return@comprehension KIO.fail(AuthError.SystemUserOnly)
            }
        } else {
            !RoleService.checkAssignable(request.roles)
            !AppUserInvitationHasRoleRepo.create(
                request.roles.map {
                    AppUserInvitationHasRoleRecord(
                        appUserInvitation = id,
                        role = it
                    )
                }
            ).orDie()
        }

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

        val emailId = !EmailService.enqueue(
            recipient = request.email,
            content = content,
            priority = EmailPriority.HIGH,
        )

        !AppUserInvitationToEmailRepo.create(
            AppUserInvitationToEmailRecord(
                appUserInvitation = id,
                email = emailId,
            )
        ).orDie()

        noData
    }

    fun acceptInvitation(
        request: AcceptInvitationRequest,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        val invitation = !AppUserInvitationRepo.consumeWithRoles(request.token).orDie()
            .onNullFail { AppUserError.InvitationNotFound }

        val record = !invitation.toAppUser(request.password)
        val id = !createUser(record)

        val roles = invitation.roles!!.map { it!!.id!! }

        if (invitation.createdBy?.id != SYSTEM_USER) {
            !RoleService.checkAssignable(roles)
        }
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
        val id = !AppUserRegistrationRepo.create(record).orDie()

        val content = !EmailService.getTemplate(
            EmailTemplateKey.USER_REGISTRATION,
            request.language,
        ).map { template ->
            template.toContent(
                EmailTemplatePlaceholder.RECIPIENT to request.firstname + " " + request.lastname,
                EmailTemplatePlaceholder.LINK to request.callbackUrl + record.token
            )
        }

        val emailId = !EmailService.enqueue(
            recipient = request.email,
            content = content,
            priority = EmailPriority.HIGH,
        )

        !AppUserRegistrationToEmailRepo.create(
            AppUserRegistrationToEmailRecord(
                appUserRegistration = id,
                email = emailId,
            )
        ).orDie()

        noData
    }

    fun verifyRegistration(
        request: VerifyRegistrationRequest,
    ): App<AppUserError, ApiResponse.Created> = KIO.comprehension {

        val registration = !AppUserRegistrationRepo.consume(request.token).orDie()
            .onNullFail { AppUserError.RegistrationNotFound }

        val clubId = if (registration.clubname != null) {
            !ClubRepo.create(
                ClubRecord(
                    UUID.randomUUID(),
                    registration.clubname!!,
                    LocalDateTime.now(),
                    SYSTEM_USER,
                    LocalDateTime.now(),
                    SYSTEM_USER
                )
            ).orDie()
        } else {
            null
        }

        val record = !registration.toAppUser(clubId)
        val userId = !createUser(record)

        KIO.ok(
            ApiResponse.Created(userId)
        )
    }


    // Error-Codes 404 and 409 are reserved by the Captcha errors. Should another 404 or 409 be created, the Error Display in the Frontend-Application needs to be updated
    fun initPasswordReset(
        request: PasswordResetInitRequest,
    ): App<Nothing, ApiResponse.NoData> = KIO.comprehension {
        val appUser = !AppUserRepo.getByEmail(request.email).orDie()

        if (appUser != null) {
            !AppUserPasswordResetRepo.deleteByAppUserId(appUser.id).orDie()

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
    ): App<AppUserError, ApiResponse.NoData> = KIO.comprehension {

        val passwordReset =
            !AppUserPasswordResetRepo.consume(token).orDie().onNullFail { AppUserError.PasswordResetNotFound }

        val newPassword = !PasswordUtilities.hash(request.password)
        !AppUserRepo.update(passwordReset.appUser) { password = newPassword }.orDie()

        noData
    }

    private fun createUser(
        record: AppUserRecord,
    ): App<Nothing, UUID> = KIO.comprehension {
        val userId = !AppUserRepo.create(record).orDie()

        !AppUserHasRoleRepo.create(
            AppUserHasRoleRecord(
                appUser = userId,
                role = USER_ROLE,
            )
        ).orDie()

        if (record.club != null) {
            !AppUserHasRoleRepo.create(
                AppUserHasRoleRecord(
                    appUser = userId,
                    role = CLUB_REPRESENTATIVE_ROLE,
                )
            ).orDie()
        }

        KIO.ok(userId)
    }


    fun deleteExpiredInvitations(): App<Nothing, Int> =
        AppUserInvitationRepo.deleteExpired().orDie()

    fun deleteExpiredRegistrations(): App<Nothing, Int> =
        AppUserRegistrationRepo.deleteExpired().orDie()

    fun deleteExpiredPasswordResets(): App<Nothing, Int> =
        AppUserPasswordResetRepo.deleteExpired().orDie()
}