package de.lambda9.ready2race.backend.app.competition.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competition.control.toDto
import de.lambda9.ready2race.backend.app.competition.entity.*
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionChallengeService
import de.lambda9.ready2race.backend.app.competitionProperties.boundary.CompetitionPropertiesService
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.toRecord
import de.lambda9.ready2race.backend.app.competitionProperties.control.toUpdateFunction
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesRequest
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationValidation
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayHasCompetitionRepo
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationError
import de.lambda9.ready2race.backend.app.eventRegistration.entity.OpenForRegistrationType
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayHasCompetitionRecord
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*

object CompetitionService {

    fun addCompetition(
        request: CompetitionPropertiesRequest,
        userId: UUID,
        eventId: UUID,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        val event = !EventRepo.get(eventId).orDie()
            .onNullFail { EventError.NotFound }

        !KIO.failOn(event.challengeEvent == true && request.setupTemplate != null) {
            CompetitionError.CompetitionSetupForbiddenForChallengeEvent
        }
        !KIO.failOn(event.challengeEvent == true && request.challengeConfig == null) {
            CompetitionError.ChallengeConfigNotProvided
        }


        val record = LocalDateTime.now().let { now ->
            CompetitionRecord(
                id = UUID.randomUUID(),
                event = eventId,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        }
        val competitionId = !CompetitionRepo.create(record).orDie()


        !CompetitionPropertiesService.checkRequestReferences(request)
        !CompetitionPropertiesService.checkCompetitionSetupTemplateExisting(request.setupTemplate)

        val competitionPropertiesRecord = request.toRecord(competitionId, null)
        val competitionPropertiesId = !CompetitionPropertiesRepo.create(competitionPropertiesRecord).orDie()

        val challengeConfig = if (request.challengeConfig != null) {
            !request.challengeConfig.toRecord(competitionPropertiesId)
        } else null

        !CompetitionPropertiesService.addCompetitionPropertiesReferences(
            namedParticipants = request.namedParticipants.map { it.toRecord(competitionPropertiesId) },
            fees = request.fees.map { it.toRecord(competitionPropertiesId) },
            challengeConfig = challengeConfig
        )

        !CompetitionSetupService.createCompetitionSetup(
            userId,
            competitionPropertiesId,
            request.setupTemplate,
            true,
        )

        // If challenge_event - create the round and match
        if (event.challengeEvent == true) {
            !CompetitionExecutionChallengeService.createChallengeSetup(competitionPropertiesRecord, userId)
        }

        KIO.ok(ApiResponse.Created(competitionId))
    }

    fun <S : CompetitionSortable> pageWithPropertiesByEvent(
        eventId: UUID,
        params: PaginationParameters<S>,
        eventDayId: UUID?,
        user: AppUserWithPrivilegesRecord?,
        scope: Privilege.Scope?,
    ): App<ServiceError, ApiResponse.Page<CompetitionDto, S>> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val (page, total) = if (scope == Privilege.Scope.GLOBAL) {
            !CompetitionRepo.pageWithPropertiesByEventAndEventDay(eventId, eventDayId, params, scope)
                .map { it -> it.traverse { it.toDto() } }.orDie() to
                !CompetitionRepo.countWithPropertiesByEventAndEventDay(eventId, eventDayId, params.search).orDie()
        } else if (scope == Privilege.Scope.OWN && user != null) {
            !CompetitionRepo.pageWithPropertiesByEventAndEventDayForClub(eventId, eventDayId, params, user)
                .map { it.traverse { it.toDto() } }.orDie() to
                !CompetitionRepo.countWithPropertiesByEventAndEventDay(eventId, eventDayId, params.search).orDie()
        } else {
            !CompetitionRepo.pagePublicByEventAndEventDay(eventId, eventDayId, params)
                .map { it -> it.traverse { it.toDto() } }.orDie() to
                !CompetitionRepo.countPublicByEventAndEventDay(eventId, eventDayId, params.search).orDie()
        }

        page.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getCompetitionWithProperties(
        competitionId: UUID,
        user: AppUserWithPrivilegesRecord?,
        scope: Privilege.Scope?,
    ): App<CompetitionError, ApiResponse> = KIO.comprehension {

        val competition = if (scope == Privilege.Scope.GLOBAL) {
            !CompetitionRepo.getScoped(competitionId, scope)
                .orDie()
                .onNullFail { CompetitionError.CompetitionNotFound }
                .map { it.toDto() }
        } else if (user != null) {
            !CompetitionRepo.getWithPropertiesForClub(competitionId, user)
                .orDie()
                .onNullFail { CompetitionError.CompetitionNotFound }
                .map { it.toDto() }
        } else {
            !CompetitionRepo.getPublic(competitionId)
                .orDie()
                .onNullFail { CompetitionError.CompetitionNotFound }
                .map { it.toDto() }
        }

        competition.map {
            ApiResponse.Dto(it)
        }
    }

    fun updateCompetition(
        request: CompetitionPropertiesRequest,
        userId: UUID,
        competitionId: UUID,
        eventId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val isChallengeEvent = !EventService.checkIsChallengeEvent(eventId)
        !KIO.failOn(isChallengeEvent && request.challengeConfig == null) {
            CompetitionError.ChallengeConfigNotProvided
        }

        !CompetitionRepo.update(competitionId) {
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().onNullFail { CompetitionError.CompetitionNotFound }


        !CompetitionPropertiesService.checkRequestReferences(request)

        !CompetitionPropertiesRepo.updateByCompetitionOrTemplate(
            competitionId,
            request.toUpdateFunction()
        )
            .orDie()

        val competitionPropertiesId =
            !CompetitionPropertiesRepo.getIdByCompetitionOrTemplateId(competitionId).orDie()
                .onNullFail { CompetitionError.CompetitionPropertiesNotFound }

        !CompetitionPropertiesService.updateCompetitionPropertiesReferences(
            competitionPropertiesId = competitionPropertiesId,
            namedParticipants = request.namedParticipants.map { it.toRecord(competitionPropertiesId) },
            fees = request.fees.map { it.toRecord(competitionPropertiesId) },
            request.challengeConfig
        )
        noData
    }

    // Competition Properties, Named Participants and Fees are deleted by cascade
    fun deleteCompetition(
        id: UUID,
    ): App<CompetitionError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !CompetitionRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(CompetitionError.CompetitionNotFound)
        } else {
            noData
        }
    }


    fun updateEventDayHasCompetition(
        request: AssignDaysToCompetitionRequest,
        userId: UUID,
        competitionId: UUID
    ): App<CompetitionError, ApiResponse.NoData> = KIO.comprehension {

        val competitionExists = !CompetitionRepo.exists(competitionId).orDie()
        if (!competitionExists) return@comprehension KIO.fail(CompetitionError.CompetitionNotFound)

        val unknownDays = !EventDayRepo.findUnknown(request.days).orDie()
        if (unknownDays.isNotEmpty()) return@comprehension KIO.fail(CompetitionError.ReferencedDaysUnknown(unknownDays))

        !EventDayHasCompetitionRepo.deleteByCompetition(competitionId).orDie()
        !EventDayHasCompetitionRepo.create(request.days.map {
            EventDayHasCompetitionRecord(
                eventDay = it,
                competition = competitionId,
                createdAt = LocalDateTime.now(),
                createdBy = userId
            )
        }).orDie()

        noData
    }

    fun getForRegistration(
        eventId: UUID,
        birthYear: Int,
        gender: Gender,
    ): App<ServiceError, ApiResponse.Dto<CompetitionsForRegistrationDto>> = KIO.comprehension {
        !EventService.checkEventExisting(eventId)

        val competitions = !CompetitionRepo.getPublicCompetitions(eventId).orDie()

        // Rating categories / age
        val (ratingCategoryAgeRestrictions) =
            !CompetitionRegistrationValidation.getRatingCategoryRestrictions(eventId)
        val validRatingCategoryAge = ratingCategoryAgeRestrictions.any { (_, ageRestriction) ->
            ((ageRestriction.from != null && ageRestriction.from <= birthYear) || ageRestriction.from == null) &&
                ((ageRestriction.to != null && ageRestriction.to >= birthYear) || ageRestriction.to == null)
        }

        // Handle late/closed registration
        !EventService.getOpenForRegistrationType(eventId).failIf({
            it == OpenForRegistrationType.CLOSED
        }) { EventRegistrationError.RegistrationClosed }

        val competitionsWithoutTeamComps = competitions.filter { competition ->
            competition.namedParticipants!!.size == 1 && competition.namedParticipants!!.first()
                .let { it!!.countMales!! + it.countFemales!! + it.countNonBinary!! + it.countMixed!! } == 1
        }

        val filtered = competitionsWithoutTeamComps
            .filter { competition ->
                competition.namedParticipants!!.first().let { namedParticipant -> // Correct gender
                    namedParticipant!!.countMixed!! > 0 || when (gender) {
                        Gender.M -> namedParticipant.countMales!! > 0
                        Gender.F -> namedParticipant.countFemales!! > 0
                        Gender.D -> namedParticipant.countNonBinary!! > 0
                    }
                }
                    && if (competition.ratingCategoryRequired!!) {
                    validRatingCategoryAge
                } else true
            }

        val dtoList = !filtered.traverse { it.toDto() }

        KIO.ok(
            ApiResponse.Dto(
                CompetitionsForRegistrationDto(
                    competitions = dtoList,
                    teamsEventOmitted = competitions.size > competitionsWithoutTeamComps.size,
                )
            )
        )
    }
}
