package de.lambda9.ready2race.backend.app.user.entity

import java.util.*

data class AppUserDto(
    val id: UUID,
    val firstname: String,
    val lastname: String,
    val email: String,
    val roles: List<UUID>,
)
