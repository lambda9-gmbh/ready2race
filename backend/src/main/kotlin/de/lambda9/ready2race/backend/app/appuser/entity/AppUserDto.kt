package de.lambda9.ready2race.backend.app.appuser.entity

import de.lambda9.ready2race.backend.app.role.entity.RoleDto
import java.util.*

data class AppUserDto(
    val id: UUID,
    val email: String,
    val firstname: String,
    val lastname: String,
    val roles: List<RoleDto>,
    val qrCodeId: String?,
)
