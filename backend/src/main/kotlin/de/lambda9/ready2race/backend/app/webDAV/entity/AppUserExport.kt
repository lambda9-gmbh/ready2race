package de.lambda9.ready2race.backend.app.webDAV.entity

import java.time.LocalDateTime
import java.util.*

data class AppUserExport(
    val id: UUID,
    val email: String,
    val firstname: String,
    val lastname: String,
    val language: String,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
    val updatedAt: LocalDateTime,
    val updatedBy: UUID?,
    val club: UUID?
)