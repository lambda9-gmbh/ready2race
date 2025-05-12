package de.lambda9.ready2race.backend.app.task.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.control.toNameDto
import de.lambda9.ready2race.backend.app.task.entity.TaskDto
import de.lambda9.ready2race.backend.app.task.entity.TaskUpsertDto
import de.lambda9.ready2race.backend.database.generated.tables.records.TaskRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.TaskWithResponsibleUsersRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*

fun TaskUpsertDto.toRecord(eventId: UUID, userId: UUID): App<Nothing, TaskRecord> = KIO.ok(
    LocalDateTime.now().let { now ->
        TaskRecord(
            id = UUID.randomUUID(),
            event = eventId,
            name = name,
            dueDate = dueDate,
            description = description,
            remark = remark,
            state = state,
            createdAt = now,
            createdBy = userId,
            updatedAt = now,
            updatedBy = userId,
        )
    }
)

fun TaskWithResponsibleUsersRecord.toDto(): App<Nothing, TaskDto> = KIO.comprehension {
    val users = !responsibleUser!!.toList().traverse { it!!.toNameDto() }

    KIO.ok(
        TaskDto(
            id = id!!,
            event = event!!,
            eventName = eventName!!,
            name = name!!,
            dueDate = dueDate,
            description = description,
            remark = remark,
            state = state!!,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
            responsibleUsers = users,
        )
    )
}
