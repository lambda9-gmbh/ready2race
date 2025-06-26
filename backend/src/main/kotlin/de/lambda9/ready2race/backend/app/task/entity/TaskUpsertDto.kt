package de.lambda9.ready2race.backend.app.task.entity

import de.lambda9.ready2race.backend.database.generated.enums.TaskState
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import java.time.LocalDateTime
import java.util.*

data class TaskUpsertDto(
    val name: String,
    val dueDate: LocalDateTime?,
    val description: String?,
    val remark: String?,
    val state: TaskState,
    val responsibleUsers: List<UUID>
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::responsibleUsers validate noDuplicates,
        )

    companion object {
        val example
            get() = TaskUpsertDto(
                name = "Name",
                dueDate = LocalDateTime.now(),
                description = "...",
                remark = "",
                state = TaskState.OPEN,
                responsibleUsers = listOf(UUID.randomUUID()),
            )
    }
}