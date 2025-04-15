package de.lambda9.ready2race.backend.pdf.elements.table

import de.lambda9.ready2race.backend.pdf.*
import org.apache.pdfbox.pdmodel.PDPage
import kotlin.math.max

data class Table(
    val withBorder: Boolean = false,
    override val children: List<Row>,
    override val padding: Padding,
) : ElementWithChildren<Row> {

    override fun render(
        context: RenderContext,
        requestNewPage: (currentContext: RenderContext) -> RenderContext
    ): RenderContext {

        var yStart = context.startPosition.y
        var parentsPadding = context.parentsPadding

        val top = context.parentsPadding.top + context.startPosition.y
        val left = context.parentsPadding.left
        val right = context.parentsPadding.right
        val bottom = context.parentsPadding.bottom

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
                bottom = contentBottom,
            ),
        )

        val lastContext = children.fold(ctx) { c, row ->
            c.startPosition.x = 0F
            row.render(c) {
                val newC = requestNewPage(c)
                yStart = 0f
                parentsPadding = newC.parentsPadding
                RenderContext(
                    page = newC.page,
                    content = newC.content,
                    startPosition = Position.zero,
                    parentsPadding = newC.parentsPadding + padding,
                )
            }
        }

        return RenderContext(
            page = lastContext.page,
            content = lastContext.content,
            startPosition = Position(
                x = 0f,
                y = yStart + lastContext.startPosition.y + padding.y,
            ),
            parentsPadding = parentsPadding,
        )
    }

    override fun endPosition(context: SizeContext): Position {
        var xMax = 0F
        var yMax = 0F

        val innerContext = SizeContext(
            parentContentWidth = context.parentContentWidth - padding.x,
            startPosition = Position(0F, 0F),
        )

        children.fold(innerContext) { c, child ->
            c.startPosition.x = 0f
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