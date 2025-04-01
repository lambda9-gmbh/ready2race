package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.pdmodel.PDDocument

data class Document(
    val pageTemplate: PageTemplate?,
    val pages: List<Page>
) {
    fun render(): PDDocument {
        val doc = PDDocument()
        pages.flatMap { page ->
            page.render(doc)
        }.forEach { doc.addPage(it) }

        return doc
    }
}