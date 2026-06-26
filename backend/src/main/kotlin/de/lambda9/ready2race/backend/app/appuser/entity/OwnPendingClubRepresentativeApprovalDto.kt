package de.lambda9.ready2race.backend.app.appuser.entity

import java.time.LocalDateTime
import java.util.*

data class OwnPendingClubRepresentativeApprovalDto(
    val clubId: UUID,
    val clubName: String,
    val createdAt: LocalDateTime,
)
