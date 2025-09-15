package de.lambda9.ready2race.backend.app.webDAV.entity.emailIndividualTemplates

import java.time.LocalDateTime
import java.util.*

data class EmailIndividualTemplateExport(
    val key: String,
    val language: String,
    val subject: String,
    val body: String,
    val bodyIsHtml: Boolean,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
    val updatedAt: LocalDateTime,
    val updatedBy: UUID?
)