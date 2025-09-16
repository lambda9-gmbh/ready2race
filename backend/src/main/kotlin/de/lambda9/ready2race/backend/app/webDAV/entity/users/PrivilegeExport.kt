package de.lambda9.ready2race.backend.app.webDAV.entity.users

import java.util.*

data class PrivilegeExport(
    val id: UUID,
    val action: String,
    val resource: String,
    val scope: String,
)