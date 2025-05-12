package de.lambda9.ready2race.backend.app.task.entity

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import de.lambda9.ready2race.backend.database.generated.enums.TaskState
import java.time.LocalDateTime
import java.util.*

data class TaskDto(
    val id: UUID,
    val event: UUID,
    val eventName: String,
    val name: String,
    val dueDate: LocalDateTime?,
    val description: String?,
    val remark: String?,
    val state: TaskState,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val responsibleUsers: List<AppUserNameDto>
)