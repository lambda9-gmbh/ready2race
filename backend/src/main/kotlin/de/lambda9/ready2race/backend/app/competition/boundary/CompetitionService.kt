package de.lambda9.ready2race.backend.app.competition.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayHasCompetitionRepo
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competition.control.toDto
import de.lambda9.ready2race.backend.app.competition.control.toRecord
import de.lambda9.ready2race.backend.app.competition.entity.*
import de.lambda9.ready2race.backend.app.competitionProperties.boundary.CompetitionPropertiesService
import de.lambda9.ready2race.backend.app.competitionProperties.control.*
import de.lambda9.ready2race.backend.app.competitionTemplate.control.CompetitionTemplateRepo
import de.lambda9.ready2race.backend.app.competitionTemplate.control.applyCompetitionProperties
import de.lambda9.ready2race.backend.app.competitionTemplate.control.toUpdateFunction
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayHasCompetitionRecord
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object CompetitionService {

    fun addCompetition(
        request: CompetitionRequest,
        userId: UUID,
        eventId: UUID,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val record = !request.toRecord(userId, eventId)
        val competitionId = !CompetitionRepo.create(record).orDie()

        //todo: @style: refactor possible?
        if (request.template != null) {
            val templateRecord = !CompetitionTemplateRepo.getWithProperties(request.template).orDie()
                .onNullFail { CompetitionError.CompetitionTemplateUnknown }
            val propsRecord = !templateRecord.applyCompetitionProperties(competitionId)

            val competitionPropertiesId = !CompetitionPropertiesRepo.create(propsRecord).orDie()

            !CompetitionPropertiesService.addCompetitionPropertiesReferences(
                namedParticipants = templateRecord.namedParticipants!!.map {
                    !it!!.applyCompetitionPropertiesHasNamedParticipant(competitionPropertiesId, it.id!!).orDie()
                },
                fees = templateRecord.fees!!.map {
                    !it!!.applyCompetitionPropertiesHasFee(competitionPropertiesId, it.id!!).orDie()
                }
            )

        } else {
            !CompetitionPropertiesService.checkRequestReferences(request.properties!!)

            val competitionPropertiesId =
                !CompetitionPropertiesRepo.create(request.properties.toRecord(competitionId, null)).orDie()

            !CompetitionPropertiesService.addCompetitionPropertiesReferences(
                namedParticipants = request.properties.namedParticipants.map { it.toRecord(competitionPropertiesId) },
                fees = request.properties.fees.map { it.toRecord(competitionPropertiesId) }
            )
        }

        KIO.ok(ApiResponse.Created(competitionId))
    }

    fun pageWithPropertiesByEvent(
        eventId: UUID,
        params: PaginationParameters<CompetitionWithPropertiesSort>,
        eventDayId: UUID?
    ): App<ServiceError, ApiResponse.Page<CompetitionDto, CompetitionWithPropertiesSort>> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val total =
            if (eventDayId == null) !CompetitionRepo.countWithPropertiesByEvent(eventId, params.search).orDie()
            else !CompetitionRepo.countWithPropertiesByEventAndEventDay(eventId, eventDayId, params.search).orDie()

        val page =
            if (eventDayId == null) !CompetitionRepo.pageWithPropertiesByEvent(eventId, params).orDie()
            else !CompetitionRepo.pageWithPropertiesByEventAndEventDay(eventId, eventDayId, params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getCompetitionWithProperties(
        competitionId: UUID
    ): App<CompetitionError, ApiResponse> = KIO.comprehension {
        val competition = !CompetitionRepo.getWithProperties(competitionId).orDie()
            .onNullFail { CompetitionError.CompetitionNotFound }
        competition.toDto().map { ApiResponse.Dto(it) }
    }

    fun updateCompetition(
        request: CompetitionRequest,
        userId: UUID,
        competitionId: UUID
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !CompetitionRepo.update(competitionId) {
            template = request.template
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().onNullFail { CompetitionError.CompetitionNotFound }

        // todo: @style: refactoring possible?
        if (request.template != null) {
            val templateRecord = !CompetitionTemplateRepo.getWithProperties(request.template).orDie()
                .onNullFail { CompetitionError.CompetitionTemplateUnknown }

            // In theory the CompetitionPropertiesRepo functions can't fail because there has to be a "competitionProperties" for the "competition" to exist
            val propertiesRecord = !CompetitionPropertiesRepo.updateByCompetitionOrTemplate(
                competitionId,
                templateRecord.toUpdateFunction()
            )
                .orDie()
                .onNullFail { CompetitionError.CompetitionPropertiesNotFound }

            !CompetitionPropertiesService.updateCompetitionPropertiesReferences(
                competitionPropertiesId = propertiesRecord.id,
                namedParticipants = templateRecord.namedParticipants!!.map {
                    !it!!.applyCompetitionPropertiesHasNamedParticipant(
                        propertiesRecord.id,
                        it.id!!
                    )
                },
                fees = templateRecord.fees!!.map {
                    !it!!.applyCompetitionPropertiesHasFee(
                        propertiesRecord.id,
                        it.id!!
                    )
                },
            )

        } else {
            !CompetitionPropertiesService.checkRequestReferences(request.properties!!)

            !CompetitionPropertiesRepo.updateByCompetitionOrTemplate(
                competitionId,
                request.properties.toUpdateFunction()
            )
                .orDie()

            val competitionPropertiesId =
                !CompetitionPropertiesRepo.getIdByCompetitionOrTemplateId(competitionId).orDie()
                    .onNullFail { CompetitionError.CompetitionPropertiesNotFound }

            !CompetitionPropertiesService.updateCompetitionPropertiesReferences(
                competitionPropertiesId = competitionPropertiesId,
                namedParticipants = request.properties.namedParticipants.map { it.toRecord(competitionPropertiesId) },
                fees = request.properties.fees.map { it.toRecord(competitionPropertiesId) }
            )
        }
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
}
