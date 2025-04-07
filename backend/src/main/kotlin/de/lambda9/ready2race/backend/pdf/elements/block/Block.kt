package de.lambda9.ready2race.backend.pdf.elements.block

import de.lambda9.ready2race.backend.pdf.*
import java.awt.Color

data class Block(
    val keepTogether: Boolean,
    override val children: List<Element>,
    override val padding: Padding,
) : ElementWithChildren<Element> {

    override fun render(
        context: RenderContext,
        requestNewPage: (currentContext: RenderContext) -> RenderContext
    ): RenderContext {

        val height = endPosition(
            SizeContext(
                context.page.mediaBox.width - context.parentsPadding.x - padding.x,
                Position.zero
            )
        ).y

        println("blockStart = ${context.parentsPadding.top}")
        println("blockHeight = $height")

        val ctx = if (keepTogether && height + context.startPosition.y + context.parentsPadding.y + padding.y > context.page.mediaBox.height) {
            requestNewPage(context)
        } else {
            context
        }

        var yStart = ctx.startPosition.y
        var parentsPadding = ctx.parentsPadding

        val c_ = ctx.content
        c_.addRect(ctx.parentsPadding.left, ctx.page.mediaBox.height - ctx.parentsPadding.top - height - ctx.startPosition.y, ctx.page.mediaBox.width - ctx.parentsPadding.x, height)
        c_.setStrokingColor(Color.BLACK)
        c_.stroke()

        val top = ctx.parentsPadding.top + ctx.startPosition.y
        val left = ctx.parentsPadding.left
        val right = ctx.parentsPadding.right
        val bottom = ctx.parentsPadding.bottom

        val contentTop = top + padding.top
        val contentLeft = left + padding.left
        val contentRight = right + padding.right
        val contentBottom = bottom + padding.bottom

        val innerContext = RenderContext(
            page = ctx.page,
            content = ctx.content,
            startPosition = Position.zero,
            parentsPadding = Padding(
                top = contentTop,
                left = contentLeft,
                right = contentRight,
                bottom = contentBottom,
            ),
        )

        val lastContext = children.fold(innerContext) { c, child ->
            child.render(c) {
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

        println("block - yStart: $yStart")
        println("blockend = ${yStart + lastContext.startPosition.y + padding.y}")

        return RenderContext(
            page = lastContext.page,
            content = lastContext.content,
            startPosition = Position(
                x = 0f,
                y = yStart + lastContext.startPosition.y + padding.y
            ),
            parentsPadding = parentsPadding,
        )
    }
}
