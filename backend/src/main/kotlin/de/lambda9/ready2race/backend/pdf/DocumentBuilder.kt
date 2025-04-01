package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.pdmodel.common.PDRectangle

class DocumentBuilder(
    private val format: PDRectangle,
    private val pageMargin: PageMargin,
) {

    internal val pages: MutableList<Page> = mutableListOf()

    fun page(
        builder: PageBuilder.() -> Unit = {}
    ) {
        val elements = PageBuilder().apply(builder).elements
        pages.add(Page(format, pageMargin, elements))
    }

}