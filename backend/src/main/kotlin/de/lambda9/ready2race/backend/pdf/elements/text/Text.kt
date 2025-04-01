package de.lambda9.ready2race.backend.pdf.elements.text

import de.lambda9.ready2race.backend.pdf.Element
import de.lambda9.ready2race.backend.pdf.RenderContext
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts

data class Text(
    val content: String,
) : Element {

    override fun render(
        context: RenderContext,
        requestNewPage: (currentContext: RenderContext) -> RenderContext
    ): RenderContext {

        var currentContext = context
        var c = currentContext.content

        var x = context.startPosition.x + context.margin.pageMarginLeft
        var y = context.page.mediaBox.height - context.startPosition.y - context.margin.pageMarginTop

        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val fontSize = 12F

        c.beginText()
        c.setFont(font, fontSize)
        c.newLineAtOffset(x, y - fontSize * font.fontDescriptor.capHeight / 1000)
        c.showText(content)
        c.endText()

        return context
    }

}
