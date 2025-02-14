package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import java.time.LocalDateTime
import java.util.*

data class AppUserInvitationDto(
    val id: UUID,
    val email: String,
    val firstname: String,
    val lastname: String,
    val language: EmailLanguage,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val createdBy: CreatedByDto?
)
