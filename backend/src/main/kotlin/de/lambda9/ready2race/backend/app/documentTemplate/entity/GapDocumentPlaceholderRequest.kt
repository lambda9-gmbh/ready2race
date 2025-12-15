package de.lambda9.ready2race.backend.app.documentTemplate.entity

import de.lambda9.ready2race.backend.text.TextAlign

data class GapDocumentPlaceholderRequest(
    val name: String?,
    val type: GapDocumentPlaceholderType,
    val page: Int,
    val relLeft: Double,
    val relTop: Double,
    val relWidth: Double,
    val relHeight: Double,
    val textAlign: TextAlign,
)
