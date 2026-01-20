package de.lambda9.ready2race.backend.app.email.entity

data class EmailTemplateDto(
    val key: EmailTemplateKey,
    val language: EmailLanguage,
    val subject: String,
    val body: String,
    val bodyIsHtml: Boolean,
    val requiredPlaceholders: List<String> = emptyList(),
    val optionalPlaceholders: List<String> = emptyList(),
)
