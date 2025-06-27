package de.lambda9.ready2race.backend.app.workType.entity

import java.time.LocalDateTime
import java.util.*

data class WorkTypeDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val color: String?,
    val minUser: Int,
    val maxUser: Int?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)