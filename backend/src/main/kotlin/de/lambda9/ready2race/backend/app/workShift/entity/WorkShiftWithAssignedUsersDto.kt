package de.lambda9.ready2race.backend.app.workShift.entity

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import java.time.LocalDateTime
import java.util.*

data class WorkShiftWithAssignedUsersDto(
    val id: UUID,
    val event: UUID,
    val title: String,
    val timeFrom: LocalDateTime,
    val timeTo: LocalDateTime,
    val eventName: String,
    val workType: UUID,
    val workTypeName: String,
    val minUser: Int,
    val maxUser: Int?,
    val remark: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val assignedUsers: List<AppUserNameDto>
)