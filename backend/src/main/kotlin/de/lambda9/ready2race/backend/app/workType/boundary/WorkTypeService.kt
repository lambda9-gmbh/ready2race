package de.lambda9.ready2race.backend.app.workType.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.workType.control.WorkTypeRepo
import de.lambda9.ready2race.backend.app.workType.control.toDto
import de.lambda9.ready2race.backend.app.workType.control.toRecord
import de.lambda9.ready2race.backend.app.workType.entity.WorkTypeDto
import de.lambda9.ready2race.backend.app.workType.entity.WorkTypeError
import de.lambda9.ready2race.backend.app.workType.entity.WorkTypeSort
import de.lambda9.ready2race.backend.app.workType.entity.WorkTypeUpsertDto
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*

object WorkTypeService {

    fun addWorkType(
        request: WorkTypeUpsertDto,
        userId: UUID
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        val created = !WorkTypeRepo.create(record).orDie()

        KIO.ok(
            ApiResponse.Created(created)
        )

    }

    fun updateWorkType(
        workTypeId: UUID,
        request: WorkTypeUpsertDto,
        userId: UUID,
    ): App<WorkTypeError, ApiResponse.NoData> = KIO.comprehension {

        !WorkTypeRepo.update(workTypeId) {
            name = request.name
            description = request.description
            color = request.color
            minUser = request.minUser
            maxUser = request.maxUser
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()
            .onNullFail { WorkTypeError.NotFound }

        KIO.ok(ApiResponse.NoData)
    }

    fun deleteWorkType(
        workTypeId: UUID
    ): App<WorkTypeError, ApiResponse.NoData> = KIO.comprehension {

        val deleted = !WorkTypeRepo.delete(workTypeId).orDie()

        if (deleted < 1) {
            KIO.fail(WorkTypeError.NotFound)
        } else {
            noData
        }
    }

    fun page(
        params: PaginationParameters<WorkTypeSort>,
    ): App<Nothing, ApiResponse.Page<WorkTypeDto, WorkTypeSort>> =
        KIO.comprehension {
            val total = !WorkTypeRepo.countByEvent(params.search).orDie()
            val page = !WorkTypeRepo.pageByEvent(params).orDie()

            page.traverse { it.toDto() }.map {
                ApiResponse.Page(
                    data = it,
                    pagination = params.toPagination(total)
                )
            }
        }

}