package de.lambda9.ready2race.backend.app.webDAV.entity.users

import java.util.*

data class RoleHasPrivilegeExport(
    val role: UUID,
    val privilege: UUID
)