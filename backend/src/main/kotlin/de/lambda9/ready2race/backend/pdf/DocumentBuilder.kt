package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.pdmodel.common.PDRectangle

class DocumentBuilder(
    private val format: PDRectangle,
    private val pagepadding: Padding,
) {

    internal val pages: MutableList<Page> = mutableListOf()

    fun page(
        builder: BlockBuilder.() -> Unit = {}
    ) {
        val elements = BlockBuilder().apply(builder).children
        pages.add(Page(format, pagepadding, elements))
    }

}