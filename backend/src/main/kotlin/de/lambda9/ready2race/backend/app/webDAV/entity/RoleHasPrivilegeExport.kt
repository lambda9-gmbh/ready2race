package de.lambda9.ready2race.backend.app.webDAV.entity

import java.util.UUID

data class RoleHasPrivilegeExport(
    val role: UUID,
    val privilege: UUID
)