package de.lambda9.ready2race.backend.sessions

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val token: String
)
