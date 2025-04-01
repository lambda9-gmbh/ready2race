package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle

data class Page(
    val format: PDRectangle,
    val pageMargin: PageMargin,
    val elements: List<Element>,
) {

    private fun newContext(document: PDDocument): RenderContext {
        val page = PDPage(format)
        val content = PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)

        return RenderContext(
            page = page,
            content = content,
            startPosition = Position(x = 0F, y = 0F),
            margin = pageMargin,
        )
    }

    fun render(document: PDDocument): List<PDPage> {

        val pages = mutableListOf<PDPage>()

        val lastContext = elements.fold(newContext(document)) { context, element ->
            element.render(context) {
                it.content.close()
                pages.add(it.page)
                newContext(document)
            }
        }

        lastContext.content.close()
        pages.add(lastContext.page)

        return pages
    }
}