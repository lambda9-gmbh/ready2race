package de.lambda9.ready2race.backend.app.eventRegistration.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.club.control.ClubRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationNamedParticipantRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationOptionalFeeRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationsWithoutTeamNumberDto
import de.lambda9.ready2race.backend.app.documentTemplate.control.DocumentTemplateRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.toPdfTemplate
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentType
import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.email.entity.EmailAttachment
import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplatePlaceholder
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDocument.control.EventDocumentRepo
import de.lambda9.ready2race.backend.app.eventParticipant.control.EventParticipantRepo
import de.lambda9.ready2race.backend.app.eventRegistration.control.*
import de.lambda9.ready2race.backend.app.eventRegistration.entity.*
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantRequirements
import de.lambda9.ready2race.backend.app.participant.control.ParticipantForEventRepo
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.ratingcategory.control.EventRatingCategoryRepo
import de.lambda9.ready2race.backend.app.ratingcategory.control.EventRatingCategoryViewRepo
import de.lambda9.ready2race.backend.app.ratingcategory.entity.AgeRestriction
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.ready2race.backend.lexiNumberComp
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.pdf.FontStyle
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.PageTemplate
import de.lambda9.ready2race.backend.pdf.document
import de.lambda9.ready2race.backend.security.RandomUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.ok
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.*
import de.lambda9.tailwind.jooq.Jooq
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

object EventRegistrationService {

    fun pageView(
        params: PaginationParameters<EventRegistrationViewSort>,
    ): App<Nothing, ApiResponse.Page<EventRegistrationViewDto, EventRegistrationViewSort>> = KIO.comprehension {
        val total = !EventRegistrationRepo.countForView(params.search).orDie()
        val page = !EventRegistrationRepo.pageForView(params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun pageForEvent(
        eventId: UUID,
        params: PaginationParameters<EventRegistrationViewSort>,
    ): App<Nothing, ApiResponse.Page<EventRegistrationViewDto, EventRegistrationViewSort>> = KIO.comprehension {
        val total = !EventRegistrationRepo.countForEvent(eventId, params.search).orDie()
        val page = !EventRegistrationRepo.pageForEvent(eventId, params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it, pagination = params.toPagination(total)
            )
        }
    }

    fun getEventRegistrationDocuments(
        eventId: UUID,
    ): App<ServiceError, ApiResponse.ListDto<EventRegistrationDocumentTypeDto>> =
        EventRegistrationRepo.getEventRegistrationDocuments(eventId).orDie()
            .onNullFail { EventError.NotFound }
            .map { ApiResponse.ListDto(it) }

    fun getEventRegistrationTemplate(
        eventId: UUID, clubId: UUID
    ): App<ServiceError, ApiResponse.Dto<EventRegistrationTemplateDto>> = KIO.comprehension {

        val type = !EventService.getOpenForRegistrationType(eventId)

        val info = !EventRegistrationRepo.getEventRegistrationInfo(eventId, type).orDie()
            .onNullFail { EventRegistrationError.EventNotFound }

        val upsertableRegistration = !EventRegistrationRepo.getEventRegistrationForUpdate(eventId, clubId, type).orDie()
            .onNullFail { EventRegistrationError.EventNotFound }

        val lockedRegistration = !EventRegistrationRepo.getLockedEventRegistration(eventId, clubId, type).orDie()
            .onNullFail { EventRegistrationError.EventNotFound }

        ok(EventRegistrationTemplateDto(info, upsertableRegistration, lockedRegistration)).map { ApiResponse.Dto(it) }
    }

    fun upsertRegistrationForEvent(
        eventId: UUID,
        registrationDto: EventRegistrationUpsertDto,
        user: AppUserWithPrivilegesRecord,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        val type = !EventService.getOpenForRegistrationType(eventId).failIf({
            it == OpenForRegistrationType.CLOSED || user.club == null
        }) { EventRegistrationError.RegistrationClosed }

        val event = !EventRepo.get(eventId).orDie().onNullDie("Existing already checked.")

        val template = !EventRegistrationRepo.getEventRegistrationInfo(eventId, type).orDie()

        val ratingCategoryAgeRestrictions = !EventRatingCategoryViewRepo.get(eventId).orDie()
            .map { list ->
                list.associate {
                    it.ratingCategory!! to AgeRestriction(
                        from = it.yearRestrictionFrom,
                        to = it.yearRestrictionTo
                    )
                }
            }

        val now = LocalDateTime.now()

        val (persistedRegistrationId, isUpdate) = !EventRegistrationRepo.findByEventAndClub(eventId, user.club!!)
            .map { it?.let { it.id to true } }.orDie() ?: (!EventRegistrationRepo.create(
            EventRegistrationRecord(
                UUID.randomUUID(),
                eventId,
                user.club!!,
                registrationDto.message,
                now,
                user.id!!,
                now,
                user.id!!
            )
        ).orDie() to false)

        val remainingRegistrations = if (isUpdate) {
            !EventRegistrationRepo.update(persistedRegistrationId) {
                message = registrationDto.message
                updatedAt = now
                updatedBy = user.id!!
            }.orDie()

            !CompetitionRegistrationRepo.deleteForEventRegistrationUpdate(persistedRegistrationId, type).orDie()

            val clubRegistrations = !CompetitionRegistrationRepo.getByClub(user.club!!).orDie()
            val grouped = clubRegistrations.groupBy { it.competition }

            val compIdsWithNewRegistrations = registrationDto.participants.flatMap {
                it.competitionsSingle?.map { it.competitionId } ?: emptyList()
            } +
                registrationDto.competitionRegistrations.mapNotNull { if (it.teams?.isNotEmpty() == true) it.competitionId else null }
            grouped.values
                .forEach { regs ->
                    if (regs.size == 1) {
                        regs.first().let {
                            it.name = if (compIdsWithNewRegistrations.contains(regs.first().competition)) "#1" else null
                            it.update()
                        }
                    } else {
                        regs.sortedWith(lexiNumberComp { it.name }).forEachIndexed { idx, rec ->
                            rec.name = "#${idx + 1}"
                            rec.update()
                        }
                    }
                }

            grouped.mapValues { (_, regs) -> regs.size }
        } else {
            null
        }

        val ratingCategoryExistsForEvent = !EventRatingCategoryRepo.existsByEvent(eventId).orDie()

        val singleCompetitionMultipleCounts =
            registrationDto.participants.flatMap { it.competitionsSingle ?: emptyList() }
                .groupingBy { it.competitionId }
                .eachCount()
                .filter { (remainingRegistrations?.get(it.key) ?: 0) + it.value > 1 }
                .mapValues { remainingRegistrations?.get(it.key) ?: 0 }.toMutableMap()

        val userInfoMap = !registrationDto.participants.traverse { pDto ->

            KIO.comprehension {
                val result = !handleSingleCompetitionRegistration(
                    pDto,
                    user.id!!,
                    user.club!!,
                    template,
                    persistedRegistrationId,
                    now,
                    singleCompetitionMultipleCounts,
                    type,
                    ratingCategoryAgeRestrictions,
                    ratingCategoryExistsForEvent
                )

                if (
                    event.challengeEvent == true &&
                    event.selfSubmission == true &&
                    pDto.email != null &&
                    (!pDto.competitionsSingle.isNullOrEmpty() || registrationDto.competitionRegistrations.flatMap {
                        it.teams ?: emptyList()
                    }.flatMap { it.namedParticipants }.flatMap { it.participantIds }.any { it == result.first })
                ) {
                    val accessTokenExists = !EventParticipantRepo.exists(eventId, result.second.id).orDie()

                    if (!accessTokenExists) {

                        val newAccessToken = RandomUtilities.token()

                        !EventParticipantRepo.create(
                            EventParticipantRecord(
                                event = eventId,
                                participant = result.second.id,
                                accessToken = newAccessToken,
                            )
                        ).orDie()

                        val content = !EmailService.getTemplate(
                            EmailTemplateKey.PARTICIPANT_CHALLENGE_REGISTERED,
                            EmailLanguage.DE, // TODO: somehow get a language
                        ).map { template ->
                            template.toContent(
                                EmailTemplatePlaceholder.RECIPIENT to pDto.firstname + " " + pDto.lastname,
                                EmailTemplatePlaceholder.EVENT to event.name,
                                EmailTemplatePlaceholder.LINK to registrationDto.callbackUrl!! + newAccessToken,
                            )
                        }

                        !EmailService.enqueue(
                            recipient = pDto.email,
                            content = content,
                        )
                    }
                }

                ok(result)
            }

        }.map { it.toMap(mutableMapOf()) }

        !registrationDto.competitionRegistrations.traverse { competitionRegistrationDto ->
            handleTeamCompetitionRegistrations(
                template,
                competitionRegistrationDto,
                persistedRegistrationId,
                user.club!!,
                now,
                user.id!!,
                userInfoMap,
                type,
                remainingRegistrations,
                ratingCategoryAgeRestrictions,
                ratingCategoryExistsForEvent,
            )
        }

        val clubName = !ClubRepo.getName(user.club!!).orDie()
        val participants = !ParticipantForEventRepo.getByClub(user.club!!).orDie()

        //TODO: @refactor: move to Repo, !don't! query for ALL clubs
        val competitions = (!Jooq.query {
            fetch(EVENT_COMPETITION_REGISTRATION)
        }.orDie()).sortedWith(lexiNumberComp { it.identifier })

        val summaryParticipants = participants.joinToString("\n") { p ->
            """
                |    [${p.gender}] ${p.firstname} ${p.lastname}${p.year?.let { " ($it)" } ?: ""}${p.externalClubName?.let { " - $it" } ?: ""}
            """.trimMargin()
        }.trimMargin()

        val summaryCompetitions = competitions.joinToString("\n") { c ->
            val teams = c.teams!!.filter { it!!.clubId == user.club }
            """
                |    ${c.identifier} ${c.name}${c.shortName?.let { " ($it)" } ?: ""}
                |        ${
                if (teams.isEmpty()) "---" else teams.sortedWith(lexiNumberComp { it?.teamName })
                    .joinToString("\n|        ") { t ->
                        val ps = t!!.participants!!.map { it!! }.sortedBy { it.role }
                        """
                        |->${t.teamName?.let { " $it" } ?: ""}
                        |            ${
                            ps.joinToString("\n|            ") { p ->
                                """
                                |[${p.role}] ${p.firstname} ${p.lastname}
                            """.trimMargin()
                            }
                        }
                    """.trimMargin()
                    }
            }
                |
            """.trimMargin()
        }

        val content = !EmailService.getTemplate(
            EmailTemplateKey.EVENT_REGISTRATION_CONFIRMATION,
            EmailLanguage.valueOf(user.language!!)
        ).map { mailTemplate ->
            mailTemplate.toContent(
                EmailTemplatePlaceholder.RECIPIENT to user.firstname + " " + user.lastname,
                EmailTemplatePlaceholder.EVENT to (event.name),
                EmailTemplatePlaceholder.CLUB to (clubName ?: ""),
                EmailTemplatePlaceholder.PARTICIPANTS to summaryParticipants,
                EmailTemplatePlaceholder.COMPETITIONS to summaryCompetitions,
            )
        }

        val attachments = !EventDocumentRepo.getDownloadsByEvent(eventId).orDie().map {
            it.map { rec ->
                EmailAttachment(
                    name = rec.name!!,
                    data = rec.data!!
                )
            }
        }

        !EmailService.enqueue(
            recipient = user.email!!,
            content = content,
            attachments = attachments
        )

        ok(ApiResponse.Created(persistedRegistrationId))

    }

    data class PersistedIdAndGender(
        val id: UUID,
        val gender: Gender,
        val year: Int
    )

    private fun handleSingleCompetitionRegistration(
        pDto: EventRegistrationParticipantUpsertDto,
        userId: UUID,
        clubId: UUID,
        template: EventRegistrationInfoDto?,
        persistedRegistrationId: UUID,
        now: LocalDateTime,
        singleCompetitionMultiCounts: MutableMap<UUID, Int>,
        type: OpenForRegistrationType,
        ratingCategoryAgeRestrictions: Map<UUID, AgeRestriction>?,
        ratingCategoryExistsForEvent: Boolean,
    ): App<EventRegistrationError, Pair<UUID, PersistedIdAndGender>> = KIO.comprehension {
        val persistedUserInfo = if (pDto.isNew == true) {
            !ParticipantRepo.create(!pDto.toRecord(userId, clubId)).orDie().map {
                PersistedIdAndGender(it, pDto.gender, pDto.year)
            }
        } else {
            val participantExists = !ParticipantRepo.existsByIdAndClub(pDto.id, clubId).orDie()
            !KIO.failOn(!participantExists) { EventRegistrationError.UpsertParticipantNotFound(pDto.id) }

            if (pDto.hasChanged == true) {
                !ParticipantRepo.update(pDto.id) {
                    firstname = pDto.firstname
                    lastname = pDto.lastname
                    gender = pDto.gender
                    year = pDto.year
                    external = pDto.external
                    externalClubName = pDto.externalClubName?.trim()?.takeIf { it.isNotBlank() }
                }.orDie()
            }

            PersistedIdAndGender(pDto.id, pDto.gender, pDto.year)
        }

        pDto.competitionsSingle?.traverse { competitionRegistrationDto ->

            KIO.comprehension {

                val competition =
                    !KIO.failOnNull(template?.competitionsSingle?.find { it.id == competitionRegistrationDto.competitionId }) {
                        EventRegistrationError.CompetitionNotFound(competitionRegistrationDto.competitionId)
                    }

                val name = singleCompetitionMultiCounts[competitionRegistrationDto.competitionId]
                    ?.plus(1)
                    ?.let {
                        singleCompetitionMultiCounts[competitionRegistrationDto.competitionId] = it
                        "#$it"
                    }

                val participantName = "${pDto.firstname} ${pDto.lastname}"

                !KIO.failOn(ratingCategoryExistsForEvent && competition.ratingCategoryRequired && competitionRegistrationDto.ratingCategory == null) {
                    EventRegistrationError.RatingCategoryMissing(
                        teamName = participantName,
                        competitionName = competition.name
                    )
                }

                // Validate age if rating category is provided
                competitionRegistrationDto.ratingCategory?.let { ratingCategoryId ->
                    val ageRestriction = !KIO.failOnNull(ratingCategoryAgeRestrictions?.get(ratingCategoryId)) {
                        EventRegistrationError.RatingCategoryNotFound(
                            id = ratingCategoryId,
                            competitionName = competition.name
                        )
                    }

                    val participantYear = pDto.year
                    val isValid =
                        ((ageRestriction.from != null && ageRestriction.from <= participantYear) || ageRestriction.from == null) &&
                            ((ageRestriction.to != null && ageRestriction.to >= participantYear) || ageRestriction.to == null)

                    !KIO.failOn(!isValid) {
                        EventRegistrationError.AgeRequirementNotMet(
                            participantName = participantName,
                            competitionName = competition.name,
                            teamName = null
                        )
                    }
                }

                val competitionRegistrationId = !CompetitionRegistrationRepo.create(
                    CompetitionRegistrationRecord(
                        UUID.randomUUID(),
                        persistedRegistrationId,
                        competitionRegistrationDto.competitionId,
                        clubId,
                        name,
                        now,
                        userId,
                        now,
                        userId,
                        isLate = type == OpenForRegistrationType.LATE,
                        ratingCategory = competitionRegistrationDto.ratingCategory
                    )
                ).orDie()

                !CompetitionRegistrationNamedParticipantRepo.create(
                    CompetitionRegistrationNamedParticipantRecord(
                        competitionRegistrationId,
                        competition.namedParticipant?.first()?.id!!,
                        persistedUserInfo.id
                    )
                ).orDie()

                competitionRegistrationDto.optionalFees?.traverse {
                    insertOptionalFee(
                        competition,
                        competitionRegistrationId,
                        it
                    )
                }?.not()

                unit
            }
        }?.not()

        ok(pDto.id to persistedUserInfo)
    }

    private fun handleTeamCompetitionRegistrations(
        template: EventRegistrationInfoDto?,
        competitionRegistrationDto: CompetitionRegistrationUpsertDto,
        persistedRegistrationId: UUID,
        clubId: UUID,
        now: LocalDateTime,
        userId: UUID,
        participantIdMap: MutableMap<UUID, PersistedIdAndGender>,
        type: OpenForRegistrationType,
        regularRegistrations: Map<UUID, Int>?,
        ratingCategoryAgeRestrictions: Map<UUID, AgeRestriction>?,
        ratingCategoryExistsForEvent: Boolean,
    ): App<EventRegistrationError, Unit> = KIO.comprehension {

        val competition =
            !KIO.failOnNull(template?.competitionsTeam?.find { it.id == competitionRegistrationDto.competitionId }) {
                EventRegistrationError.CompetitionNotFound(
                    competitionRegistrationDto.competitionId
                )
            }

        val participantIds =
            competitionRegistrationDto.teams?.flatMap { teamDto -> teamDto.namedParticipants.flatMap { np -> np.participantIds } }
                ?: emptyList()

        !KIO.failOn(participantIds.size != participantIds.toSet().size) {
            val invalidParticipants = participantIds.groupBy { it }.filter { it.value.size > 1 }.keys
            EventRegistrationError.ParticipantDuplicateInCompetition(
                participantIds = invalidParticipants,
                competitionName = competition.name,
            )
        }


        var count = competitionRegistrationDto.teams
            ?.takeIf { (regularRegistrations?.get(competitionRegistrationDto.competitionId) ?: 0) + it.size > 1 }
            ?.let { regularRegistrations?.get(competitionRegistrationDto.competitionId) ?: 0 }

        competitionRegistrationDto.teams?.traverse { teamDto ->
            KIO.comprehension {

                val name = count
                    ?.plus(1)
                    ?.let {
                        count = it
                        "#$it"
                    }

                !KIO.failOn(ratingCategoryExistsForEvent && competition.ratingCategoryRequired && teamDto.ratingCategory == null) {
                    EventRegistrationError.RatingCategoryMissing(
                        teamName = name ?: "team",
                        competitionName = competition.name
                    )
                }


                // Validate age if rating category is provided
                teamDto.ratingCategory?.let { ratingCategoryId ->
                    val ageRestriction = !KIO.failOnNull(ratingCategoryAgeRestrictions?.get(ratingCategoryId)) {
                        EventRegistrationError.RatingCategoryNotFound(
                            id = ratingCategoryId,
                            competitionName = competition.name
                        )
                    }

                    val participantYears = teamDto.namedParticipants
                        .flatMap { it.participantIds }
                        .mapNotNull { participantIdMap[it]?.year }

                    participantYears.forEach { year ->
                        val isValid =
                            ((ageRestriction.from != null && ageRestriction.from <= year) || ageRestriction.from == null) &&
                                ((ageRestriction.to != null && ageRestriction.to >= year) || ageRestriction.to == null)

                        !KIO.failOn(!isValid) {
                            EventRegistrationError.AgeRequirementNotMet(
                                participantName = null,
                                teamName = name,
                                competitionName = competition.name
                            )
                        }
                    }

                }


                val competitionRegistrationId = !CompetitionRegistrationRepo.create(
                    CompetitionRegistrationRecord(
                        UUID.randomUUID(),
                        persistedRegistrationId,
                        competitionRegistrationDto.competitionId,
                        clubId,
                        name,
                        now,
                        userId,
                        now,
                        userId,
                        isLate = type == OpenForRegistrationType.LATE,
                        ratingCategory = teamDto.ratingCategory
                    )
                ).orDie()

                !teamDto.namedParticipants.traverse { namedParticipantDto ->
                    insertNamedParticipant(
                        namedParticipantDto,
                        participantIdMap,
                        competitionRegistrationId,
                        competition
                    )
                }

                teamDto.optionalFees?.traverse { insertOptionalFee(competition, competitionRegistrationId, it) }?.not()

                unit
            }
        }?.not()

        ok(Unit)
    }

    private fun insertNamedParticipant(
        namedParticipantDto: CompetitionRegistrationNamedParticipantUpsertDto,
        participantIdMap: MutableMap<UUID, PersistedIdAndGender>,
        competitionRegistrationId: UUID,
        competition: EventRegistrationCompetitionDto
    ): App<EventRegistrationError, Unit> = KIO.comprehension {

        val counts: MutableMap<Gender, Int> = mutableMapOf(
            Gender.M to 0,
            Gender.F to 0,
            Gender.D to 0,
        )

        !namedParticipantDto.participantIds.traverse { participantId ->
            KIO.comprehension {
                val persistedUserInfo = !KIO.failOnNull(participantIdMap[participantId]) {
                    EventRegistrationError.TeamParticipantNotFound(
                        participantId = participantId,
                        competitionName = competition.name,
                        namedParticipantId = namedParticipantDto.namedParticipantId
                    )
                }

                counts[persistedUserInfo.gender] = (counts[persistedUserInfo.gender] ?: 0) + 1

                CompetitionRegistrationNamedParticipantRepo.create(
                    CompetitionRegistrationNamedParticipantRecord(
                        competitionRegistrationId,
                        namedParticipantDto.namedParticipantId,
                        persistedUserInfo.id
                    )
                ).orDie()
            }
        }

        val requirements =
            !KIO.failOnNull(competition.namedParticipant?.find { np -> np.id == namedParticipantDto.namedParticipantId }) {
                EventRegistrationError.NamedParticipantNotFound(
                    id = namedParticipantDto.namedParticipantId,
                    competitionName = competition.name
                )
            }

        val enoughMixedSlots = checkEnoughMixedSpots(
            requirements = NamedParticipantRequirements(
                countMales = requirements.countMales,
                countFemales = requirements.countFemales,
                countNonBinary = requirements.countNonBinary,
                countMixed = requirements.countMixed,
            ),
            counts
        )

        !KIO.failOn(
            (requirements.countMixed
                + requirements.countMales
                + requirements.countFemales
                + requirements.countNonBinary
                ) != counts.values.sum()
                || !enoughMixedSlots
        ) {
            EventRegistrationError.InvalidTeamDistribution(
                namedParticipantId = namedParticipantDto.namedParticipantId,
                competitionName = competition.name
            )
        }

        unit
    }

    fun checkEnoughMixedSpots(
        requirements: NamedParticipantRequirements,
        counts: Map<Gender, Int>
    ): Boolean {
        val overflowMales = (counts[Gender.M] ?: 0) - requirements.countMales
        val overflowFemales = (counts[Gender.F] ?: 0) - requirements.countFemales
        val overflowNonBinary = (counts[Gender.D] ?: 0) - requirements.countNonBinary


        val maleFemaleSlotsAvailableForNonBinary =
            if (overflowMales < 0) overflowMales * -1 else 0 + if (overflowFemales < 0) overflowFemales * -1 else 0

        val enoughMixedSlots = ((if (overflowMales > 0) overflowMales else 0) +
            (if (overflowFemales > 0) overflowFemales else 0) +
            (if (overflowNonBinary - maleFemaleSlotsAvailableForNonBinary > 0) (overflowNonBinary - maleFemaleSlotsAvailableForNonBinary) else 0)) <= requirements.countMixed

        return enoughMixedSlots
    }

    private fun insertOptionalFee(
        competition: EventRegistrationCompetitionDto,
        competitionRegistrationId: UUID,
        optionalFee: UUID
    ): App<EventRegistrationError, Unit> = KIO.comprehension {

        !KIO.failOn(competition.fees.find { it.id == optionalFee }?.required != false) {
            EventRegistrationError.FeeNotFound(
                id = optionalFee,
                competitionName = competition.name
            )
        }

        CompetitionRegistrationOptionalFeeRepo.create(
            CompetitionRegistrationOptionalFeeRecord(
                competitionRegistrationId,
                optionalFee
            )
        ).orDie()
    }

    fun getRegistration(
        id: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): App<ServiceError, ApiResponse.Dto<EventRegistrationViewDto>> = KIO.comprehension {

        val record = !EventRegistrationRepo.getView(id).orDie().onNullFail {
            EventRegistrationError.NotFound
        }

        !KIO.failOn(
            scope == Privilege.Scope.OWN && record.clubId != user.club
        ) { AuthError.PrivilegeMissing }

        record.toDto().map { ApiResponse.Dto(it) }
    }

    fun deleteRegistration(
        id: UUID,
    ): App<EventRegistrationError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !EventRegistrationRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(EventRegistrationError.NotFound)
        } else {
            noData
        }
    }

    fun getRegistrationsWithoutTeamNumber(
        eventId: UUID
    ): App<EventError, ApiResponse.ListDto<CompetitionRegistrationsWithoutTeamNumberDto>> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val eventRegistrationResult = !EventRegistrationRepo.getRegistrationResult(eventId).orDie()

        val result = mutableListOf<CompetitionRegistrationsWithoutTeamNumberDto>()

        eventRegistrationResult?.competitions?.forEach { competition ->
            competition?.teams?.forEach { registrationTeam ->
                if (registrationTeam?.teamNumber == null) {
                    result.add(
                        CompetitionRegistrationsWithoutTeamNumberDto(
                            competitionId = competition.id!!,
                            competitionIdentifier = competition.identifier!!,
                            competitionName = competition.name!!,
                            registrationId = registrationTeam?.id!!,
                            registrationClub = registrationTeam.clubName!!,
                            registrationName = registrationTeam.teamName,
                        )
                    )
                }
            }
        }

        ok(
            ApiResponse.ListDto(result)
        )
    }

    fun finalizeRegistrations(
        userId: UUID,
        eventId: UUID,
        keepNumbers: Boolean,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val registrations = !CompetitionRegistrationRepo.allForEvent(eventId).orDie()

        registrations.groupBy { it.competition }.values.forEach { registrationsForSameComp ->
            if (keepNumbers) {

                val highestNumber = registrationsForSameComp.mapNotNull { it.teamNumber }.maxOfOrNull { it } ?: 0

                registrationsForSameComp
                    .filter { it.teamNumber == null }
                    .shuffled()
                    .forEachIndexed { idx, record ->
                        record.teamNumber = highestNumber + idx + 1
                        record.updatedBy = userId
                        record.updatedAt = LocalDateTime.now()
                        record.update()
                    }
            } else {
                registrationsForSameComp.shuffled().forEachIndexed { idx, record ->
                    record.teamNumber = idx + 1
                    record.updatedBy = userId
                    record.updatedAt = LocalDateTime.now()
                    record.update()
                }
            }
        }

        !EventRegistrationReportRepo.delete(eventId).orDie()

        !generateResultDocument(eventId)

        noData
    }

    fun downloadResult(
        eventId: UUID,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        !EventService.checkEventExisting(eventId)

        EventRegistrationReportRepo.getDownload(eventId).orDie()
            .onNullFail { EventRegistrationError.RegistrationsNotFinalized }.map {
                ApiResponse.File(
                    name = it.name!!,
                    bytes = it.data!!,
                )
            }
    }

    private fun generateResultDocument(
        eventId: UUID,
    ): App<EventRegistrationError, Pair<String, ByteArray>> = KIO.comprehension {

        val result = !EventRegistrationRepo.getRegistrationResult(eventId).orDie()
            .onNullFail { EventRegistrationError.EventNotFound }

        val pdfTemplate = !DocumentTemplateRepo.getAssigned(DocumentType.REGISTRATION_REPORT, eventId).orDie()
            .andThenNotNull { it.toPdfTemplate() }

        val filename = "registration_result_${result.eventName!!.replace(" ", "-")}_${
            LocalDateTime.now().format(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
            )
        }.pdf"

        val bytes = buildPdf(
            data = EventRegistrationResultData.fromPersisted(result) { it.isLate == false },
            template = pdfTemplate,
        )

        val documentRecord = EventRegistrationReportRecord(
            event = eventId,
            name = filename,
            createdAt = LocalDateTime.now(),
        )
        val id = !EventRegistrationReportRepo.create(documentRecord).orDie()
        val dataRecord = EventRegistrationReportDataRecord(
            resultDocument = id,
            data = bytes,
        )
        !EventRegistrationReportDataRepo.create(dataRecord).orDie()

        ok(filename to bytes)
    }

    fun buildPdf(
        data: EventRegistrationResultData,
        template: PageTemplate?,
    ): ByteArray {
        val doc = document(template) {
            // TODO: Instead don't allow this action
            if (data.competitionRegistrations.isEmpty()) {
                page {
                    text { "keine Wettkämpfe in dieser Veranstaltung" }
                }
            }
            data.competitionRegistrations.sortedWith(lexiNumberComp { it.identifier }).forEach { competition ->
                page {
                    block(
                        padding = Padding(0f, 0f, 0f, 20f)
                    ) {
                        text(
                            fontStyle = FontStyle.BOLD,
                            fontSize = 14f,
                        ) {
                            "Wettkampf / "
                        }
                        text(
                            fontSize = 12f,
                            newLine = false,
                        ) {
                            "Competition"
                        }

                        table(
                            padding = Padding(5f, 10f, 0f, 0f)
                        ) {
                            column(0.1f)
                            column(0.25f)
                            column(0.65f)

                            row {
                                cell {
                                    text(
                                        fontSize = 12f,
                                    ) { competition.identifier }
                                }
                                cell {
                                    competition.shortName?.let {
                                        text(
                                            fontSize = 12f,
                                        ) { it }
                                    }
                                }
                                cell {
                                    text(
                                        fontSize = 12f,
                                    ) { competition.name }
                                }
                            }
                        }
                    }

                    if (competition.teams.isEmpty()) {
                        text(
                            fontStyle = FontStyle.BOLD,
                            fontSize = 11f,
                        ) { "Wettkampf entfällt / " }
                        text(
                            newLine = false,
                        ) { "Competition cancelled" }
                    } else {
                        val categories = competition.teams.groupBy { it.ratingCategory?.id }
                        categories.toList().sortedBy { it.second.first().ratingCategory?.name }
                            .forEach { (_, categoryTeams) ->
                                val category = categoryTeams.first().ratingCategory

                                block(
                                    padding = Padding(bottom = 20f)
                                ) {

                                    block(
                                        padding = Padding(bottom = 10f),
                                    ) {
                                        text(
                                            fontStyle = FontStyle.BOLD,
                                            newLine = false,
                                            ) { category?.name ?: "Unkategorisiert / " }
                                        text(
                                            newLine = false,
                                        ) { "uncategorized" }
                                    }

                                    categoryTeams.groupBy { it.clubId }.forEach { (_, clubTeams) ->
                                        clubTeams.sortedWith(lexiNumberComp { it.name }).forEach { team ->
                                            block(
                                                padding = Padding(0f, 0f, 0f, 7.5f)
                                            ) {

                                                text(
                                                    fontStyle = FontStyle.BOLD
                                                ) { team.actualClubName?: team.clubName }
                                                block (
                                                    padding = Padding(left = 5f)
                                                ) {
                                                    text(
                                                        fontStyle = FontStyle.BOLD,
                                                        fontSize = 8f,
                                                    ) {
                                                        "gemeldet von / "
                                                    }
                                                    text(
                                                        newLine = false,
                                                        fontSize = 8f,
                                                    ) { "registered by" + "   ${team.clubName}" }
                                                    team.name?.let {
                                                        text(
                                                            newLine = false,
                                                            fontSize = 8f,
                                                        ) { " | $it" }
                                                    }
                                                }

                                                table(
                                                    padding = Padding(5f, 0f, 0f, 0f),
                                                    withBorder = true,
                                                ) {
                                                    column(0.15f)
                                                    column(0.05f)
                                                    column(0.2f)
                                                    column(0.2f)
                                                    column(0.1f)
                                                    column(0.3f)

                                                    team.participants
                                                        .sortedBy { it.role }
                                                        .forEachIndexed { idx, member ->
                                                            row(
                                                                color = if (idx % 2 == 1) Color(
                                                                    230,
                                                                    230,
                                                                    230
                                                                ) else null,
                                                            ) {
                                                                cell {
                                                                    text { member.role }
                                                                }
                                                                cell {
                                                                    text { member.gender.name }
                                                                }
                                                                cell {
                                                                    text { member.firstname }
                                                                }
                                                                cell {
                                                                    text { member.lastname }
                                                                }
                                                                cell {
                                                                    text { member.year.toString() }
                                                                }
                                                                cell {
                                                                    text { member.externalClubName ?: team.clubName }
                                                                }
                                                            }
                                                        }
                                                }
                                            }
                                        }
                                    }

                                }
                            }
                    }
                }
            }
        }

        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()

        val bytes = out.toByteArray()
        out.close()

        return bytes
    }
}