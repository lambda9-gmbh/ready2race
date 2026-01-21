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
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object TimeslotService {

    fun getTimeslotsByEventDay(
        eventDayId: UUID
    ): App<Nothing, ApiResponse.ListDto<TimeslotDto>> = KIO.comprehension {
        val timeslots = !TimeslotRepo.getByEventDay(eventDayId).orDie()
        val dtoList = timeslots.map { it.toDto() }
        KIO.ok(ApiResponse.ListDto(dtoList))
    }

    fun addTimeslotToEventDay(
        request: TimeslotRequest,
        userId: UUID,
        eventDayId: UUID
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        !EventDayRepo.exists(eventDayId).orDie()
            .onNullFail { EventDayError.EventDayNotFound }

        val record = TimeslotRecord(
            id = UUID.randomUUID(),
            eventDay = eventDayId,
            name = request.name,
            description = request.description,
            startTime = request.startTime,
            endTime = request.endTime,
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
}