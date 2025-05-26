package de.lambda9.ready2race.backend.app.task.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.task.control.*
import de.lambda9.ready2race.backend.app.task.entity.TaskDto
import de.lambda9.ready2race.backend.app.task.entity.TaskError
import de.lambda9.ready2race.backend.app.task.entity.TaskUpsertDto
import de.lambda9.ready2race.backend.app.task.entity.TaskWithResponsibleUsersSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.enums.TaskState
import de.lambda9.ready2race.backend.database.generated.tables.records.TaskHasResponsibleUserRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*

object TaskService {

    fun addTask(
        request: TaskUpsertDto,
        eventId: UUID,
        userId: UUID
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(eventId, userId)
        val created = !TaskRepo.create(record).orDie()

        !TaskHasResponsibleUserRepo.create(
            request.responsibleUsers.map {
                TaskHasResponsibleUserRecord(
                    appUser = it,
                    task = created
                )
            }
        ).orDie()

        KIO.ok(
            ApiResponse.Created(created)
        )

    }

    fun updateTask(
        taskId: UUID,
        request: TaskUpsertDto,
        userId: UUID,
        eventId: UUID,
    ): App<TaskError, ApiResponse.NoData> = KIO.comprehension {

        !TaskRepo.update(taskId, eventId) {
            name = request.name
            description = request.description
            dueDate = request.dueDate
            remark = request.remark
            state = request.state
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()
            .onNullFail { TaskError.NotFound }

        !TaskHasResponsibleUserRepo.deleteAll(taskId).orDie()

        !TaskHasResponsibleUserRepo.create(
            request.responsibleUsers.map {
                TaskHasResponsibleUserRecord(
                    appUser = it,
                    task = taskId
                )
            }
        ).orDie()

        KIO.ok(ApiResponse.NoData)
    }

    fun deleteTask(
        taskId: UUID,
        eventId: UUID,
    ): App<TaskError, ApiResponse.NoData> = KIO.comprehension {

        val deleted = !TaskRepo.delete(taskId, eventId).orDie()

        if (deleted < 1) {
            KIO.fail(TaskError.NotFound)
        } else {
            noData
        }
    }

    fun page(
        params: PaginationParameters<TaskWithResponsibleUsersSort>,
        eventId: UUID
    ): App<Nothing, ApiResponse.Page<TaskDto, TaskWithResponsibleUsersSort>> =
        KIO.comprehension {
            val total = !TaskWithResponsibleUsersRepo.countByEvent(eventId, params.search).orDie()
            val page = !TaskWithResponsibleUsersRepo.pageByEvent(eventId, params).orDie()

            page.traverse { it.toDto() }.map {
                ApiResponse.Page(
                    data = it,
                    pagination = params.toPagination(total)
                )
            }
        }

    fun pageOpenForUser(
        params: PaginationParameters<TaskWithResponsibleUsersSort>,
        userId: UUID
    ): App<Nothing, ApiResponse.Page<TaskDto, TaskWithResponsibleUsersSort>> =
        KIO.comprehension {

            val states = listOf(TaskState.OPEN, TaskState.IN_PROGRESS)

            val total = !TaskWithResponsibleUsersRepo.countByUser(userId, states, params.search).orDie()
            val page = !TaskWithResponsibleUsersRepo.pageByUser(userId, states, params).orDie()

            page.traverse { it.toDto() }.map {
                ApiResponse.Page(
                    data = it,
                    pagination = params.toPagination(total)
                )
            }
        }

}