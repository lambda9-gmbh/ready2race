package de.lambda9.ready2race.backend.app.auth.entity

import java.util.*

data class LoginDto(
    val id: UUID,
    val privileges: List<PrivilegeDto>,
)
