package de.lambda9.ready2race.backend.pdf.elements.table

import de.lambda9.ready2race.backend.pdf.*
import java.awt.Color
import kotlin.math.max

data class Cell(
    val width: Float,
    val color: Color?,
    val withBorder: Boolean,
    override val children: List<Element>,
    override val padding: Padding,
) : ElementWithChildren<Element> {

    override fun render(
        context: RenderContext,
        requestNewPage: (currentContext: RenderContext) -> RenderContext
    ): RenderContext {

        val rowWidth = context.page.mediaBox.width - context.parentsPadding.x
        val cellWidth = rowWidth * width

        val top = context.parentsPadding.top
        val left = context.parentsPadding.left + context.startPosition.x
        val right = context.page.mediaBox.width - left - cellWidth
        val bottom = context.parentsPadding.bottom

        val x = left
        val h = context.page.mediaBox.height - context.parentsPadding.y
        val y = context.page.mediaBox.height - top - h
        val w = cellWidth

        val c = context.content
        if (color != null) {
            c.addRect(x, y, w, h)
            c.setNonStrokingColor(color)
            c.fill()
            c.setNonStrokingColor(Color.BLACK)
        }
        if (withBorder) {
            c.addRect(x, y, w, h)
            c.setStrokingColor(Color.BLACK)
            c.setLineWidth(1F)
            c.stroke()
        }

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
                // cells should not break
                c
            }
        }

        val height = lastCtx.startPosition.y + padding.y

        context.startPosition.x += cellWidth
        context.startPosition.y += height

        return context
    }

    override fun endPosition(context: SizeContext): Position {
        var xMax = 0F
        var yMax = 0F

        val cellWidth = context.parentContentWidth * width

        val innerContext = SizeContext(
            parentContentWidth = cellWidth - padding.x,
            startPosition = Position(0f, 0f),
        )

        children.fold(innerContext) { c, child ->
            val pos = child.endPosition(c)
            xMax = max(xMax, pos.x)
            yMax = max(yMax, pos.y)
            c.startPosition.x = pos.x
            c.startPosition.y = pos.y
            c
        }

        return Position(
            x = context.startPosition.x + xMax + padding.x,
            y = context.startPosition.y + yMax + padding.y,
        )
    }

}
