package de.lambda9.ready2race.backend.calls.responses

import com.fasterxml.jackson.annotation.JsonIgnore
import io.ktor.http.*

data class ApiError(
    val status: HttpStatusCode,
    val message: String,
    @JsonIgnore val headers: Map<String, String> = emptyMap(),
    val details: Map<String, Any?>? = null,
    val errorCode: ErrorCode? = null
)
