package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle

data class Page(
    val template: PageTemplate?,
    val elements: List<Element>,
) {

    private val pageMargin = template?.pageMargin ?: PageMargin.default
    private val pageTemplate = template?.let {
        Loader.loadPDF(it.bytes).use { doc ->
            doc.getPage(0)
        }
    } ?: PDPage(PDRectangle.A4)

    private fun newContext(document: PDDocument): RenderContext {
        val page = pageTemplate
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