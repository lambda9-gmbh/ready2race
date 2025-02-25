package de.lambda9.ready2race.backend.app.race.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayHasRaceRepo
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.race.control.RaceRepo
import de.lambda9.ready2race.backend.app.race.control.toDto
import de.lambda9.ready2race.backend.app.race.control.toRecord
import de.lambda9.ready2race.backend.app.race.entity.*
import de.lambda9.ready2race.backend.app.raceProperties.boundary.RacePropertiesService
import de.lambda9.ready2race.backend.app.raceProperties.control.*
import de.lambda9.ready2race.backend.app.raceTemplate.control.RaceTemplateRepo
import de.lambda9.ready2race.backend.app.raceTemplate.control.applyRaceProperties
import de.lambda9.ready2race.backend.app.raceTemplate.control.toUpdateFunction
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayHasRaceRecord
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object RaceService {

    fun addRace(
        request: RaceRequest,
        userId: UUID,
        eventId: UUID,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val record = !request.toRecord(userId, eventId)
        val raceId = !RaceRepo.create(record).orDie()

        if (request.template != null) {
            val templateRecord = !RaceTemplateRepo.getWithProperties(request.template).orDie()
                .onNullFail { RaceError.RaceTemplateUnknown }
            val propsRecord = !templateRecord.applyRaceProperties(raceId)

            val racePropertiesId = !RacePropertiesRepo.create(propsRecord).orDie()


            val namedParticipantRecords = templateRecord.namedParticipants?.map {
                !it!!.applyRacePropertiesHasNamedParticipant(racePropertiesId, it.id!!).orDie()
            }
            if (namedParticipantRecords != null) {
                !RacePropertiesHasNamedParticipantRepo.create(namedParticipantRecords).orDie()
            }
        } else {
            !RacePropertiesService.checkRequestReferences(request.properties!!)

            val racePropertiesId = !RacePropertiesRepo.create(request.properties.toRecord(raceId, null)).orDie()

            !RacePropertiesHasNamedParticipantRepo.create(request.properties.namedParticipants.map {
                it.toRecord(
                    racePropertiesId
                )
            }).orDie()
        }

        KIO.ok(ApiResponse.Created(raceId))
    }

    fun pageWithPropertiesByEvent(
        eventId: UUID,
        params: PaginationParameters<RaceWithPropertiesSort>,
        eventDayId: UUID?
    ): App<ServiceError, ApiResponse.Page<RaceDto, RaceWithPropertiesSort>> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val total =
            if (eventDayId == null) !RaceRepo.countWithPropertiesByEvent(eventId, params.search).orDie()
            else !RaceRepo.countWithPropertiesByEventAndEventDay(eventId, eventDayId, params.search).orDie()

        val page =
            if (eventDayId == null) !RaceRepo.pageWithPropertiesByEvent(eventId, params).orDie()
            else !RaceRepo.pageWithPropertiesByEventAndEventDay(eventId, eventDayId, params).orDie()

        page.forEachM { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getRaceWithProperties(
        raceId: UUID
    ): App<RaceError, ApiResponse> = KIO.comprehension {
        val race = !RaceRepo.getWithProperties(raceId).orDie().onNullFail { RaceError.RaceNotFound }
        race.toDto().map { ApiResponse.Dto(it) }
    }

    fun updateRace(
        request: RaceRequest,
        userId: UUID,
        raceId: UUID
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !RaceRepo.update(raceId) {
            template = request.template
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().onNullFail { RaceError.RaceNotFound }

        if (request.template != null) {
            val templateRecord = !RaceTemplateRepo.getWithProperties(request.template).orDie()
                .onNullFail { RaceError.RaceTemplateUnknown }

            // In theory the RacePropertiesRepo functions can't fail because there has to be a "raceProperties" for the "race" to exist
            val propertiesRecord = !RacePropertiesRepo.updateByRaceOrTemplate(raceId, templateRecord.toUpdateFunction())
                .orDie()
                .onNullFail { RaceError.RacePropertiesNotFound }

            // delete and re-add the named participant entries
            !RacePropertiesHasNamedParticipantRepo.deleteManyByRaceProperties(propertiesRecord.id).orDie()
            val namedParticipantRecords = templateRecord.namedParticipants?.map {
                !it!!.applyRacePropertiesHasNamedParticipant(raceId, it.id!!)
            }
            if (namedParticipantRecords != null) {
                !RacePropertiesHasNamedParticipantRepo.create(namedParticipantRecords).orDie()
            }

        } else {
            !RacePropertiesService.checkRequestReferences(request.properties!!)

            !RacePropertiesRepo.updateByRaceOrTemplate(raceId, request.properties.toUpdateFunction())
                .orDie()

            val racePropertiesId =
                !RacePropertiesRepo.getIdByRaceOrTemplateId(raceId).orDie()
                    .onNullFail { RaceError.RacePropertiesNotFound }

            // delete and re-add the named participant entries
            !RacePropertiesHasNamedParticipantRepo.deleteManyByRaceProperties(racePropertiesId).orDie()
            !RacePropertiesHasNamedParticipantRepo.create(request.properties.namedParticipants.map {
                it.toRecord(
                    racePropertiesId
                )
            }).orDie()
        }
        noData
    }

    // Race Properties and Named Participants are deleted by cascade
    fun deleteRace(
        id: UUID,
    ): App<RaceError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !RaceRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(RaceError.RaceNotFound)
        } else {
            noData
        }
    }


    fun updateEventDayHasRace(
        request: AssignDaysToRaceRequest,
        userId: UUID,
        raceId: UUID
    ): App<RaceError, ApiResponse.NoData> = KIO.comprehension {

        val raceExists = !RaceRepo.exists(raceId).orDie()
        if (!raceExists) return@comprehension KIO.fail(RaceError.RaceNotFound)

        val unknownDays = !EventDayRepo.findUnknown(request.days).orDie()
        if (unknownDays.isNotEmpty()) return@comprehension KIO.fail(RaceError.ReferencedDaysUnknown(unknownDays))

        !EventDayHasRaceRepo.deleteByRace(raceId).orDie()
        !EventDayHasRaceRepo.create(request.days.map {
            EventDayHasRaceRecord(
                eventDay = it,
                race = raceId,
                createdAt = LocalDateTime.now(),
                createdBy = userId
            )
        }).orDie()

        noData
    }
}
