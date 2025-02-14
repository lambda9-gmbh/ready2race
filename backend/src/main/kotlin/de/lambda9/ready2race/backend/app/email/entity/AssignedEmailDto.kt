package de.lambda9.ready2race.backend.app.email.entity

import java.time.LocalDateTime

data class AssignedEmailDto(
    val recipient: String,
    val sentAt: LocalDateTime?,
    val lastErrorAt: LocalDateTime?,
    val lastError: String?,
)
