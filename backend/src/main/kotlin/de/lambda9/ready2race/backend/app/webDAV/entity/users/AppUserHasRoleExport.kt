package de.lambda9.ready2race.backend.app.webDAV.entity.users

import java.util.*

data class AppUserHasRoleExport(
    val appUser: UUID,
    val role: UUID
)