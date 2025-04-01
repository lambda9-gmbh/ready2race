package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream

data class RenderContext(
    val page: PDPage,
    val content: PDPageContentStream,
    var startPosition: Position,
    val margin: PageMargin,
)
