package de.lambda9.ready2race.backend.app.club.entity

import java.time.LocalDateTime
import java.util.*

// This is a public dto - keep information simple
data class ClubDto(
    val id: UUID,
    val name: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)