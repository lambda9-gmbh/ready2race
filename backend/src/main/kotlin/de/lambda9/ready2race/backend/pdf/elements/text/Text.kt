package de.lambda9.ready2race.backend.pdf.elements.text

import de.lambda9.ready2race.backend.pdf.*
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts

data class Text(
    val newLine: Boolean,
    val content: String,
    val fontSize: Float,
    val lineHeight: Float,
    val fontStyle: FontStyle,
    override val padding: Padding,
) : Element {

    private val font = when (fontStyle) {
        FontStyle.NORMAL -> PDType1Font(Standard14Fonts.FontName.HELVETICA)
        FontStyle.BOLD -> PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        FontStyle.ITALIC -> PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)
        FontStyle.BOLD_ITALIC -> PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE)
    }

    private val width = font.getStringWidth(content) / 1000 * fontSize
    private val height = lineHeight * fontSize * font.fontDescriptor.capHeight / 1000

    override fun render(
        context: RenderContext,
        requestNewPage: (currentContext: RenderContext) -> RenderContext
    ): RenderContext {

        var currentContext = context
        var c = currentContext.content

        val xStart = context.startPosition.x + getX0(context)
        val yStart = getY0(context) - context.startPosition.y

        println("content: $content, xStart: $xStart")
        println("yStart: $yStart")

        c.beginText()
        c.setFont(font, fontSize)

        var (x, y) = if (newLine) {
            getX0(context) to yStart - height
        } else {
            xStart to yStart + fontSize / 4
        }

        c.newLineAtOffset(x, y)
        c.showText(content)
        c.endText()

        x += width

        context.startPosition.x += x - xStart
        context.startPosition.y += yStart - y + fontSize / 4

        return context
    }

    override fun endPosition(position: Position): Position =
        if (newLine) {
            Position(
                x = width,
                y = position.y + height + fontSize / 4,
            )
        } else {
            Position(
                x = position.x + width,
                y = position.y,
            )
        }

}
