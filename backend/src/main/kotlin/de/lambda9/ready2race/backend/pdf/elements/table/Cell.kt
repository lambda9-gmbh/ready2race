package de.lambda9.ready2race.backend.pdf.elements.table

import de.lambda9.ready2race.backend.pdf.*
import java.awt.Color

data class Cell(
    val width: Float,
    val color: Color?,
    override val children: List<Element>,
    override val padding: Padding,
) : ElementWithChildren<Element> {

    override fun render(
        context: RenderContext,
        requestNewPage: (currentContext: RenderContext) -> RenderContext
    ): RenderContext {

        val top = context.parentsPadding.top
        val left = context.parentsPadding.left + context.startPosition.x
        val right = context.page.mediaBox.width - left - width
        val bottom = context.parentsPadding.bottom

        val x = left
        val h = context.page.mediaBox.height - context.parentsPadding.y
        val y = context.page.mediaBox.height - top - h
        val w = width + padding.x

        val c = context.content
        if (color != null) {
            c.addRect(x, y, w, h)
            c.setNonStrokingColor(color)
            c.fill()
            c.setNonStrokingColor(Color.BLACK)
        }
        c.addRect(x, y, w, h)
        c.setStrokingColor(Color.BLACK)
        c.setLineWidth(1F)
        c.stroke()

        val contentTop = top + padding.top
        val contentLeft = left + padding.left
        val contentRight = right + padding.right
        val contentBottom = bottom + padding.bottom

        val ctx = RenderContext(
            page = context.page,
            content = context.content,
            startPosition = Position(0F, 0F),
            parentsPadding = Padding(
                top = contentTop,
                left = contentLeft,
                right = contentRight,
                bottom = contentBottom
            ),
        )

        val lastCtx = children.fold(ctx) { c, element ->
            element.render(c) {
                // TODO @Incomplete
                c
            }
        }

        val height = lastCtx.startPosition.y + padding.y

        context.startPosition.x += width + padding.x
        context.startPosition.y += height

        return context
    }

}
