package de.lambda9.ready2race.backend.app.eventDay.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.eventDay.entity.*
import de.lambda9.ready2race.backend.app.eventDay.control.TimeslotRepo
import de.lambda9.ready2race.backend.app.eventDay.control.toDto
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.TimeslotRecord
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object TimeslotService {

    fun pageByEventDay(
        eventDayId: UUID,
        params: PaginationParameters<TimeslotSort>,
    ): App<Nothing, ApiResponse.Page<TimeslotDto, TimeslotSort>> = KIO.comprehension {

        val total = !TimeslotRepo.countByEventDay(eventDayId, params.search).orDie()

        val page =
            !TimeslotRepo.pageByEventDay(eventDayId, params).orDie()

        val list = page.map { it.toDto() }
        KIO.ok(ApiResponse.Page(
            data = list,
            pagination = params.toPagination(total)
        ))
    }

    fun getTimeslot(
        timeslotId: UUID
    ): App<ServiceError, ApiResponse> = KIO.comprehension {

        val timeslot = !TimeslotRepo.getTimeslot(timeslotId).orDie()
            .onNullFail { EventDayError.TimeslotNotFound }

        KIO.ok(ApiResponse.Dto(timeslot.toDto()))
    }

    fun addTimeslotToEventDay(
        request: TimeslotRequest,
        userId: UUID,
        eventDayId: UUID
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {
        if (request.matchReference != null) {
            !TimeslotRepo.timeslotForCompetitionUnitExists(request.matchReference).orDie().onTrueFail {
                EventDayError.CompetitionUnitAlreadyHasTimeslot
            }
        } else if (request.roundReference != null) {
            !TimeslotRepo.timeslotForCompetitionUnitExists(request.roundReference).orDie().onTrueFail {
                EventDayError.CompetitionUnitAlreadyHasTimeslot
            }
            !TimeslotRepo.lowerCompetitionUnitTimeslotAlreadyExists(request.roundReference).orDie().onTrueFail {
                EventDayError.LowerCompetitionUnitAlreadyHasTimeslot
            }
        } else if (request.competitionReference != null) {
            !TimeslotRepo.timeslotForCompetitionUnitExists(request.competitionReference).orDie().onTrueFail {
                EventDayError.CompetitionUnitAlreadyHasTimeslot
            }
            !TimeslotRepo.lowerCompetitionUnitTimeslotAlreadyExists(request.competitionReference).orDie().onTrueFail {
                EventDayError.LowerCompetitionUnitAlreadyHasTimeslot
            }
        }



        !EventDayRepo.exists(eventDayId).orDie()
            .onNullFail { EventDayError.EventDayNotFound }

        val record = TimeslotRecord(
            id = UUID.randomUUID(),
            eventDay = eventDayId,
            name = request.name,
            description = request.description,
            startTime = request.startTime,
            endTime = request.endTime,
            competitionReference = request.competitionReference,
            roundReference = request.roundReference,
            matchReference = request.matchReference,
            createdAt = LocalDateTime.now(),
            createdBy = userId,
            updatedAt = LocalDateTime.now(),
            updatedBy = userId
        )

        val id = !TimeslotRepo.create(record).orDie()
        KIO.ok(ApiResponse.Created(id))
    }

    fun updateTimeslot(
        request: TimeslotRequest,
        userId: UUID,
        timeslotId: UUID
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val timeslotExists = !TimeslotRepo.exists(timeslotId).orDie()
        if (!timeslotExists) KIO.fail(EventDayError.TimeslotNotFound)

        !TimeslotRepo.update(timeslotId) {
            name = request.name
            description = request.description
            startTime = request.startTime
            endTime = request.endTime
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        KIO.ok(ApiResponse.NoData)
    }

    fun deleteTimeslot(
        timeslotId: UUID
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val timeslotExists = !TimeslotRepo.exists(timeslotId).orDie()
        if (!timeslotExists) KIO.fail(EventDayError.TimeslotNotFound)

        val deleted = !TimeslotRepo.delete(timeslotId).orDie()

        if (deleted < 1) {
            KIO.fail(EventDayError.EventDayNotFound)
        } else {
            noData
        }
    }

    fun getOwnTimeslotById(id:UUID) = KIO.comprehension {
        val data = !TimeslotRepo.findSelfIncludingTimeslotById(id).orDie()
        KIO.ok(data?.toDto())
    }
}