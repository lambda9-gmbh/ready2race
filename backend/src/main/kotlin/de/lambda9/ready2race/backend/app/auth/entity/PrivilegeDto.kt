package de.lambda9.ready2race.backend.app.auth.entity

import java.util.*

data class PrivilegeDto(
    val id: UUID,
    val action: String,
    val resource: String,
    val scope: String,
)
