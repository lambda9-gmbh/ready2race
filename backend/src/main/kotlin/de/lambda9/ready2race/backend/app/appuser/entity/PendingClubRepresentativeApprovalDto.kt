package de.lambda9.ready2race.backend.app.appuser.entity

import java.time.LocalDateTime
import java.util.*

data class PendingClubRepresentativeApprovalDto(
    val userId: UUID,
    val firstName: String,
    val lastName: String,
    val email: String,
    val createdAt: LocalDateTime,
)