package de.lambda9.ready2race.backend.app.email.entity

data class EmailPlaceholders(
    val required: List<String> = emptyList(),
    val optional: List<String> = emptyList(),
)
