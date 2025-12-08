package de.lambda9.ready2race.backend.app.appuser.boundary

import de.lambda9.ready2race.backend.afterNow
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.appuser.control.*
import de.lambda9.ready2race.backend.app.appuser.entity.*
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.club.control.ClubRepo
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionError
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationNamedParticipantRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationOptionalFeeRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationValidation
import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.email.entity.EmailPriority
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplatePlaceholder
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventRegistration.control.EventRegistrationRepo
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationError
import de.lambda9.ready2race.backend.app.eventRegistration.entity.OpenForRegistrationType
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.role.boundary.RoleService
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.ADMIN_ROLE
import de.lambda9.ready2race.backend.database.CLUB_REPRESENTATIVE_ROLE
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.USER_ROLE
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.ready2race.backend.security.RandomUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration.Companion.days

object AppUserService {

    fun AppUserRecord.fullName(): String = "$firstname $lastname"

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

    fun getIncludingAllAdmins(
        id: UUID,
    ): App<AppUserError, ApiResponse.Dto<AppUserDto>> = KIO.comprehension {
        val record = !AppUserRepo.getWithRolesIncludingAllAdmins(id).orDie().onNullFail { AppUserError.NotFound }
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
        noClub: Boolean?
    ): App<Nothing, ApiResponse.Page<AppUserDto, AppUserWithRolesSort>> = KIO.comprehension {
        val total = !AppUserRepo.countWithRoles(params.search, noClub).orDie()
        val page = !AppUserRepo.pageWithRoles(params, noClub).orDie()

        page.traverse { it.appUserDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun pageIncludingAdmins(
        params: PaginationParameters<EveryAppUserWithRolesSort>,
        noClub: Boolean?
    ): App<Nothing, ApiResponse.Page<AppUserDto, EveryAppUserWithRolesSort>> = KIO.comprehension {
        val total = !AppUserRepo.countWithRolesIncludingAdmins(params.search, noClub).orDie()
        val page = !AppUserRepo.pageWithRolesIncludingAdmins(params, noClub).orDie()

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

        val targetIsAdmin = !AppUserHasRoleRepo.exists(targetUserId, ADMIN_ROLE).orDie()

        !KIO.failOn(
            scope == Privilege.Scope.OWN
                && (requestingUserId != targetUserId || request.roles.isNotEmpty())
                || targetIsAdmin && requestingUserId != targetUserId && requestingUserId != SYSTEM_USER
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
        val id = !createUser(record, listOf(USER_ROLE))

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

    fun register(
        request: AppUserRegisterRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !EmailAddressRepo.exists(request.email).orDie()
            .onTrueFail { AppUserError.EmailAlreadyInUse }

        request.clubname?.let {
            val nameExists = !ClubRepo.nameExists(it).orDie()
            !KIO.failOn(nameExists) {
                AppUserError.ClubNameAlreadyExists
            }
        }

        val record = !request.toAppUserRegistrationRecord(registrationLifeTime)
        val id = !AppUserRegistrationRepo.create(record).orDie()


        // Competition registrations
        if (request.registerToSingleCompetitions.isNotEmpty()) {

            val birthYear = request.birthYear!! // Verified in validation

            val competitions =
                !CompetitionRepo.getByIds(request.registerToSingleCompetitions.map { it.competitionId }).orDie()

            !KIO.failOn(request.registerToSingleCompetitions.size != competitions.size) {
                CompetitionError.CompetitionNotFound
            }
            val eventId = competitions.first().event!!

            // Validate single competitions
            !CompetitionRegistrationValidation.validateSingleCompetitions(competitions)

            !EventService.getOpenForRegistrationType(eventId).failIf({
                it == OpenForRegistrationType.CLOSED
            }) { EventRegistrationError.RegistrationClosed }

            // Get rating category restrictions
            val (ratingCategoryAgeRestrictions, ratingCategoryExistsForEvent) =
                !CompetitionRegistrationValidation.getRatingCategoryRestrictions(eventId)

            // Competition registration
            val competitionRegistrationRecords = request.registerToSingleCompetitions.map {
                AppUserRegistrationCompetitionRegistrationRecord(
                    id = UUID.randomUUID(),
                    appUserRegistration = id,
                    competitionId = it.competitionId,
                    ratingcategory = it.ratingCategory,
                )
            }
            !AppUserRegistrationCompetitionRegistrationRepo.create(competitionRegistrationRecords).orDie()

            !request.registerToSingleCompetitions.traverse { competitionRegistration ->
                KIO.comprehension {

                    val competition = competitions.first { it.id == competitionRegistration.competitionId }

                    val competitionRegRecord =
                        competitionRegistrationRecords.first { it.competitionId == competitionRegistration.competitionId }


                    val participantName = "${request.firstname} ${request.lastname}"

                    // Validate rating category required
                    !CompetitionRegistrationValidation.validateRatingCategoryRequired(
                        ratingCategoryExistsForEvent = ratingCategoryExistsForEvent,
                        competitionRatingCategoryRequired = competition.ratingCategoryRequired!!,
                        providedRatingCategory = competitionRegistration.ratingCategory,
                        teamName = participantName,
                        competitionName = competition.name!!
                    )

                    // Validate age if rating category is provided
                    competitionRegistration.ratingCategory?.let { ratingCategoryId ->
                        !CompetitionRegistrationValidation.validateAgeRestriction(
                            birthYear = birthYear,
                            ratingCategoryId = ratingCategoryId,
                            ratingCategoryRestrictions = ratingCategoryAgeRestrictions,
                            participantName = participantName,
                            teamName = null,
                            competitionName = competition.name!!
                        )
                    }

                    // Optional fees
                    if (competitionRegistration.optionalFees?.isNotEmpty() ?: false) {
                        val optionalCompetitionFees =
                            competition.fees!!.filter { it?.required == false }.map { it!!.id!! }

                        val feeRecords = !competitionRegistration.optionalFees!!.traverse { registrationFee ->
                            KIO.comprehension {
                                !CompetitionRegistrationValidation.validateOptionalFee(
                                    feeId = registrationFee,
                                    competitionFees = optionalCompetitionFees,
                                    competitionName = competition.name!!
                                )
                                KIO.ok(
                                    AppUserRegistrationCompetitionRegistrationFeeRecord(
                                        appUserRegistrationCompetitionRegistration = competitionRegRecord.id,
                                        optionalFee = registrationFee,
                                    )
                                )
                            }
                        }
                        !AppUserRegistrationCompetitionRegistrationFeeRepo.create(feeRecords).orDie()
                    }
                    unit
                }
            }

            noData
        }

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
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        // Fetch
        val registration = !AppUserRegistrationRepo.get(request.token).orDie()
            .onNullFail { AppUserError.RegistrationNotFound }
        val competitionRegistrations =
            !AppUserRegistrationCompetitionRegistrationRepo.getByAppUserRegistration(registration.id).orDie()
        val feeRecsForRegistration = !AppUserRegistrationCompetitionRegistrationFeeRepo.getByCompetitionRegistrations(
            competitionRegistrations.map { it.id }
        ).orDie()
        val competitionRegToFees =
            competitionRegistrations.associate { compRec -> compRec.id to feeRecsForRegistration.filter { it.appUserRegistrationCompetitionRegistration == compRec.id } }


        // Delete
        !AppUserRegistrationCompetitionRegistrationFeeRepo.deleteByCompetitionRegistrations(
            competitionRegistrations.map { it.id }
        ).orDie()
        !AppUserRegistrationCompetitionRegistrationRepo.deleteByAppUserRegistration(registration.id).orDie()
        registration.delete()

        val now = LocalDateTime.now()

        val clubId = if (registration.clubname != null) {

            val nameExists = !ClubRepo.nameExists(registration.clubname!!).orDie()
            !KIO.failOn(nameExists) {
                AppUserError.ClubNameAlreadyExists
            }

            !ClubRepo.create(
                ClubRecord(
                    UUID.randomUUID(),
                    registration.clubname!!,
                    now,
                    SYSTEM_USER,
                    now,
                    SYSTEM_USER
                )
            ).orDie()
        } else {
            registration.clubId!!
        }

        val userId = if (registration.clubname != null) {
            val record = !registration.toAppUser(clubId)
            !createUser(record, listOf(USER_ROLE, CLUB_REPRESENTATIVE_ROLE))
        } else {
            val record = !registration.toAppUser(clubId = null)
            val userId = !createUser(record, listOf(USER_ROLE))

            !AppUserClubRepresentativeApprovalRepo.create(
                AppUserClubRepresentativeApprovalRecord(
                    id = UUID.randomUUID(),
                    appUser = userId,
                    club = clubId,
                    approved = null,
                    createdAt = now,
                    updatedAt = now,
                    updatedBy = userId
                )
            ).orDie()

            userId
        }

        // Handle competition registrations if present

        if (competitionRegistrations.isNotEmpty()) {
            val participantId = !ParticipantRepo.create(
                ParticipantRecord(
                    id = UUID.randomUUID(),
                    club = clubId,
                    firstname = registration.firstname,
                    lastname = registration.lastname,
                    year = registration.year!!,
                    gender = registration.gender!!,
                    phone = null,
                    external = false,
                    externalClubName = null,
                    createdAt = now,
                    createdBy = userId,
                    updatedAt = now,
                    updatedBy = userId,
                    email = registration.email,
                )
            ).orDie()

            val competitions = !CompetitionRepo.getByIds(competitionRegistrations.map { it.competitionId }).orDie()
            val eventId = competitions.first().event!!

            val eventRegistration = !EventRegistrationRepo.getByEventAndClub(eventId, clubId).orDie()
            val eventRegistrationId = if (eventRegistration == null) {
                !EventRegistrationRepo.create(
                    EventRegistrationRecord(
                        id = UUID.randomUUID(),
                        event = eventId,
                        club = clubId,
                        message = null,
                        createdAt = now,
                        createdBy = userId,
                        updatedAt = now,
                        updatedBy = userId,
                        eventDocumentsOfficiallyAcceptedAt = now,
                        eventDocumentsOfficiallyAcceptedBy = userId,
                    )
                ).orDie()
            } else eventRegistration.id!!


            val openForRegistrationType = !EventService.getOpenForRegistrationType(eventId).failIf({
                it == OpenForRegistrationType.CLOSED
            }) { EventRegistrationError.RegistrationClosed }

            !competitionRegistrations.traverse { competitionRegistration ->
                KIO.comprehension {
                    val competition = competitions.first { it.id == competitionRegistration.competitionId }

                    val existingCount = !CompetitionRegistrationRepo.countForCompetitionAndClub(
                        competitionRegistration.competitionId,
                        clubId
                    ).orDie()
                    val registrationName = when {
                        existingCount < 1 -> null
                        existingCount == 1 -> {
                            val first = !CompetitionRegistrationRepo.getByCompetitionAndClub(
                                competitionRegistration.competitionId,
                                clubId
                            ).orDie()
                                .map { it.singleOrNull() }.onNullDie("Count returned 1 row, select returned NOT 1 row.")
                            first.name = "#1"
                            first.update()
                            "#2"
                        }

                        else -> "#${existingCount + 1}"
                    }

                    val competitionRegistrationId = !CompetitionRegistrationRepo.create(
                        CompetitionRegistrationRecord(
                            id = UUID.randomUUID(),
                            eventRegistration = eventRegistrationId,
                            competition = competitionRegistration.competitionId,
                            club = clubId,
                            name = registrationName,
                            createdAt = now,
                            createdBy = userId,
                            updatedAt = now,
                            updatedBy = userId,
                            teamNumber = null,
                            isLate = openForRegistrationType == OpenForRegistrationType.LATE,
                            ratingCategory = competitionRegistration.ratingcategory
                        )
                    ).orDie()

                    !CompetitionRegistrationNamedParticipantRepo.create(
                        CompetitionRegistrationNamedParticipantRecord(
                            competitionRegistration = competitionRegistrationId,
                            namedParticipant = competition.namedParticipants!!.first()?.id!!,
                            participant = participantId
                        )
                    ).orDie()
                    val fees = competitionRegToFees[competitionRegistration.id]!!
                    !fees.traverse { feeRecord ->
                        CompetitionRegistrationOptionalFeeRepo.create(
                            CompetitionRegistrationOptionalFeeRecord(
                                competitionRegistrationId,
                                feeRecord.optionalFee
                            )
                        ).orDie()
                    }

                    unit
                }
            }
        }

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
        roles: List<UUID>
    ): App<Nothing, UUID> = KIO.comprehension {
        val userId = !AppUserRepo.create(record).orDie()

        val appUserHasRoleRecords = roles.map {
            AppUserHasRoleRecord(
                appUser = userId,
                role = it,
            )
        }

        !AppUserHasRoleRepo.create(
            appUserHasRoleRecords
        ).orDie()

        KIO.ok(userId)
    }


    fun deleteExpiredInvitations(): App<Nothing, Int> =
        AppUserInvitationRepo.deleteExpired().orDie()

    fun deleteExpiredRegistrations(): App<Nothing, Int> =
        AppUserRegistrationRepo.deleteExpired().orDie()

    fun deleteExpiredPasswordResets(): App<Nothing, Int> =
        AppUserPasswordResetRepo.deleteExpired().orDie()


    fun getAllAppUsersForEvent(
        eventId: UUID,
        params: PaginationParameters<AppUserForEventSort>
    ): App<EventError, ApiResponse.Page<AppUserForEventDto, AppUserForEventSort>> = KIO.comprehension {
        val total = !AppUserForEventRepo.countForEvent(eventId, params.search).orDie()
        val page = !AppUserForEventRepo.pageForEvent(eventId, params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getPendingClubRepresentativeApprovals(
        clubId: UUID,
    ): App<ServiceError, ApiResponse.ListDto<PendingClubRepresentativeApprovalDto>> = KIO.comprehension {
        val pending = !AppUserClubRepresentativeApprovalRepo.getPendingByClubId(clubId).orDie()

        KIO.ok(ApiResponse.ListDto(pending))
    }

    fun updateClubRepresentativeApproval(
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
        targetUser: UUID,
        approve: Boolean,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val approval = !AppUserClubRepresentativeApprovalRepo.getOpenByUserId(targetUser).orDie()
            .onNullFail { AppUserError.NotFound }

        !KIO.failOn(scope == Privilege.Scope.OWN && approval.club != user.club) {
            AuthError.PrivilegeMissing
        }

        val userToApprove = !AppUserRepo.get(targetUser).orDie()
            .onNullFail { AppUserError.NotFound }

        !KIO.failOn(userToApprove.club != null) {
            AppUserError.ClubRepresentativeCanNotBeApproved
        }

        !AppUserClubRepresentativeApprovalRepo.update(approval) {
            approved = approve
            updatedAt = LocalDateTime.now()
            updatedBy = user.id
        }.orDie()

        if (approve) {
            !AppUserRepo.updateByRecord(userToApprove) {
                club = approval.club
                updatedAt = LocalDateTime.now()
                updatedBy = user.id
            }.orDie()

            !AppUserHasRoleRepo.create(
                AppUserHasRoleRecord(
                    appUser = targetUser,
                    role = CLUB_REPRESENTATIVE_ROLE,
                )
            ).orDie()
        }

        noData
    }
}