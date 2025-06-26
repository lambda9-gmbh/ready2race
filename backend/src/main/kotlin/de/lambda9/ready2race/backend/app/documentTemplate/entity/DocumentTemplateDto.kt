package de.lambda9.ready2race.backend.app.documentTemplate.entity

import java.util.*

data class DocumentTemplateDto(
    val id: UUID,
    val name: String,
    val pagePaddingTop: Double?,
    val pagePaddingLeft: Double?,
    val pagePaddingRight: Double?,
    val pagePaddingBottom: Double?,
)
