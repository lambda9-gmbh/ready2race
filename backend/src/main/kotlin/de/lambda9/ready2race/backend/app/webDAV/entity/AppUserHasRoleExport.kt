package de.lambda9.ready2race.backend.app.webDAV.entity

import java.util.UUID

data class AppUserHasRoleExport(
    val appUser: UUID,
    val role: UUID
)