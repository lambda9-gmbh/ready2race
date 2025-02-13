package de.lambda9.ready2race.backend.app.role.entity

import de.lambda9.ready2race.backend.app.auth.entity.PrivilegeDto
import java.util.UUID

data class RoleDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val privileges: List<PrivilegeDto>
)
