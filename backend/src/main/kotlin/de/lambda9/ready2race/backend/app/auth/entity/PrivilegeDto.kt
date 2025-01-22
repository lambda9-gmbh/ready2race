package de.lambda9.ready2race.backend.app.auth.entity

data class PrivilegeDto(
    val action: String,
    val resource: String,
    val scope: String,
)
