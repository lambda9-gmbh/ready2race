package de.lambda9.ready2race.backend.app.workShift.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.time.LocalDateTime
import java.util.*

data class WorkShiftUpsertDto(
    val workType: UUID,
    val timeFrom: LocalDateTime,
    val timeTo: LocalDateTime,
    val minUser: Int,
    val maxUser: Int?,
    val remark: String?,
    val assignedUsers: List<UUID>
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.Valid

    companion object {
        val example
            get() = WorkShiftUpsertDto(
                workType = UUID.randomUUID(),
                timeFrom = LocalDateTime.now(),
                timeTo = LocalDateTime.now().plusHours(1),
                minUser = 1,
                maxUser = 5,
                remark = "Remark",
                assignedUsers = listOf(UUID.randomUUID())
            )
    }
}