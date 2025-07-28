package de.lambda9.ready2race.backend.app.appUserWithQrCode.entity

import de.lambda9.ready2race.backend.app.role.entity.RoleDto
import java.time.LocalDateTime
import java.util.*

data class AppUserWithQrCodeDto(
    val id: UUID,
    val firstname: String,
    val lastname: String,
    val email: String,
    val club: UUID?,
    val roles: List<RoleDto>,
    val qrCodeId: String,
    val eventId: UUID,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
)