package de.lambda9.ready2race.backend.app.auth.entity

data class LoginRequest(
    val email: String,
    val password: String,
)
