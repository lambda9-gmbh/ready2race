package de.lambda9.ready2race.backend.http

import io.ktor.http.*

data class ApiError(
    val status: HttpStatusCode,
    val message: String,
    val headers: Map<String, String> = emptyMap(),
    val details: Map<String, Any?> = emptyMap(),
)
