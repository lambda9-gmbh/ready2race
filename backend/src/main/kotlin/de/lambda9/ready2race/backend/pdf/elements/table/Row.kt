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

        val height = endPosition(Position(0f, 0f)).y

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

        val lastContext = children.fold(ctx) { c, cell ->
            c.startPosition.y = 0F

            cell.render(c) {
                // TODO @Incomplete
                c
            }
        }

        context.startPosition.y += height + padding.y

        return context
    }

    override fun endPosition(position: Position): Position {
        var xMax = 0F
        var yMax = 0F

        val lastPosition = children.fold(position) { p, child ->
            xMax = max(xMax, p.x)
            yMax = max(yMax, p.y)
            p.y = position.y
            child.endPosition(p)
        }

        xMax = max(xMax, lastPosition.x)
        yMax = max(yMax, lastPosition.y)

        return Position(
            x = xMax,
            y = yMax,
        )
    }

}
