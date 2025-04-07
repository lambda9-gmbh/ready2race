package de.lambda9.ready2race.backend.pdf.elements.table

import de.lambda9.ready2race.backend.pdf.*
import kotlin.math.max

data class Row(
    override val children: List<Cell>,
    override val padding: Padding,
) : ElementWithChildren<Cell> {

    override fun render(
        context: RenderContext,
        requestNewPage: (currentContext: RenderContext) -> RenderContext
    ): RenderContext {

        val height = endPosition(
            SizeContext(
                context.page.mediaBox.width - context.parentsPadding.x - padding.x,
                Position(
                    0f, 0f
                )
            )
        ).y

        val ctx = if (height + context.startPosition.y + context.parentsPadding.y + padding.y > context.page.mediaBox.height) {
            requestNewPage(context)
        } else {
            context
        }

        val top = ctx.parentsPadding.top + ctx.startPosition.y
        val left = ctx.parentsPadding.left
        val right = ctx.parentsPadding.right
        val bottom = ctx.page.mediaBox.height - top - height

        val contentTop = top + padding.top
        val contentLeft = left + padding.left
        val contentRight = right + padding.right
        val contentBottom = bottom + padding.bottom

        val innerContext = RenderContext(
            page = ctx.page,
            content = ctx.content,
            startPosition = Position(0F, 0F),
            parentsPadding = Padding(
                top = contentTop,
                left = contentLeft,
                right = contentRight,
                bottom = contentBottom,
            ),
        )

        val lastContext = children.fold(innerContext) { c, cell ->
            cell.render(c) {
                c //rows should not break
            }
        }

        return RenderContext(
            page = lastContext.page,
            content = lastContext.content,
            startPosition = Position(
                x = 0f,
                y = ctx.startPosition.y + height + padding.y
            ),
            parentsPadding = ctx.parentsPadding,
        )
    }

    override fun endPosition(context: SizeContext): Position {
        var xMax = 0F
        var yMax = 0F

        val innerContext = SizeContext(
            parentContentWidth = context.parentContentWidth - padding.x,
            startPosition = Position(0f, 0f),
        )

        children.fold(innerContext) { c, child ->
            c.startPosition.y = 0f
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
