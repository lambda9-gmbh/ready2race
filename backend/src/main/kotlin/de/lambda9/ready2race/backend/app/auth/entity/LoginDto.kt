package de.lambda9.ready2race.backend.app.auth.entity

data class LoginDto(
    val privilegesGlobal: List<Privilege>,
    val privilegesBound: List<Privilege>,
)
