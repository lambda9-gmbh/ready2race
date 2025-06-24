package de.lambda9.ready2race.backend.app.workShift.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.workShift.control.*
import de.lambda9.ready2race.backend.app.workShift.entity.WorkShiftError
import de.lambda9.ready2race.backend.app.workShift.entity.WorkShiftUpsertDto
import de.lambda9.ready2race.backend.app.workShift.entity.WorkShiftWithAssignedUsersDto
import de.lambda9.ready2race.backend.app.workShift.entity.WorkShiftWithAssignedUsersSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.WorkShiftHasUserRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*

object WorkShiftService {

    fun addWorkShift(
        eventId: UUID,
        request: WorkShiftUpsertDto,
        userId: UUID
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(eventId, userId)
        val created = !WorkShiftRepo.create(record).orDie()

        !WorkShiftHasUserRepo.create(
            request.assignedUsers.map {
                WorkShiftHasUserRecord(
                    appUser = it,
                    workShift = created
                )
            }
        ).orDie()

        KIO.ok(
            ApiResponse.Created(created)
        )

    }

    fun updateWorkShift(
        workShiftId: UUID,
        request: WorkShiftUpsertDto,
        userId: UUID,
    ): App<WorkShiftError, ApiResponse.NoData> = KIO.comprehension {

        !WorkShiftRepo.update(workShiftId) {
            timeFrom = request.timeFrom
            timeTo = request.timeTo
            workType = request.workType
            minUser = request.minUser
            maxUser = request.maxUser
            remark = request.remark
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()
            .onNullFail { WorkShiftError.NotFound }

        !WorkShiftHasUserRepo.deleteAll(workShiftId).orDie()

        !WorkShiftHasUserRepo.create(
            request.assignedUsers.map {
                WorkShiftHasUserRecord(
                    appUser = it,
                    workShift = workShiftId
                )
            }
        ).orDie()

        KIO.ok(ApiResponse.NoData)
    }

    fun deleteWorkShift(
        workShiftId: UUID
    ): App<WorkShiftError, ApiResponse.NoData> = KIO.comprehension {

        val deleted = !WorkShiftRepo.delete(workShiftId).orDie()

        if (deleted < 1) {
            KIO.fail(WorkShiftError.NotFound)
        } else {
            noData
        }
    }

    fun page(
        params: PaginationParameters<WorkShiftWithAssignedUsersSort>,
        eventId: UUID,
        timeFrom: LocalDateTime? = null,
        timeTo: LocalDateTime? = null,
    ): App<Nothing, ApiResponse.Page<WorkShiftWithAssignedUsersDto, WorkShiftWithAssignedUsersSort>> =
        KIO.comprehension {
            val total = !WorkShiftWithAssignedUsersRepo.countByEvent(eventId, params.search, timeFrom, timeTo).orDie()
            val page = !WorkShiftWithAssignedUsersRepo.pageByEvent(eventId, params, timeFrom, timeTo).orDie()

            page.traverse { it.toDto() }.map {
                ApiResponse.Page(
                    data = it,
                    pagination = params.toPagination(total)
                )
            }
        }

    fun pageByUser(
        params: PaginationParameters<WorkShiftWithAssignedUsersSort>,
        userId: UUID,
        timeFrom: LocalDateTime? = null,
        timeTo: LocalDateTime? = null,
    ): App<Nothing, ApiResponse.Page<WorkShiftWithAssignedUsersDto, WorkShiftWithAssignedUsersSort>> =
        KIO.comprehension {
            val total = !WorkShiftWithAssignedUsersRepo.countByUser(userId, params.search, timeFrom, timeTo).orDie()
            val page = !WorkShiftWithAssignedUsersRepo.pageByUser(userId, params, timeFrom, timeTo).orDie()

            page.traverse { it.toDto() }.map {
                ApiResponse.Page(
                    data = it,
                    pagination = params.toPagination(total)
                )
            }
        }

}