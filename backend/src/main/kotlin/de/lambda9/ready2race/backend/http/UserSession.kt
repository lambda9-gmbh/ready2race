package de.lambda9.ready2race.backend.http

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val token: String
)
