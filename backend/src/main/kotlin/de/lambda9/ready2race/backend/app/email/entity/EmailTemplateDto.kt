package de.lambda9.ready2race.backend.app.email.entity

data class EmailTemplateDto(
    val subject: String,
    val body: String,
    val bodyIsHtml: Boolean,
)
