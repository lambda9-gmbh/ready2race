package de.lambda9.ready2race.backend.app.appuser.entity

import java.util.*

data class AppUserDto(
    val id: UUID,
    val email: String,
    val firstname: String,
    val lastname: String,
    val roles: List<UUID>,
)
