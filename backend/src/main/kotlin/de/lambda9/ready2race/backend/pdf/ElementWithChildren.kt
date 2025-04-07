package de.lambda9.ready2race.backend.pdf

import kotlin.math.max

interface ElementWithChildren<T : Element> : Element {

    val children: List<T>

    override fun endPosition(context: SizeContext): Position {
        var xMax = 0F
        var yMax = 0F

        val innerContext = SizeContext(
            parentContentWidth = context.parentContentWidth - padding.x,
            startPosition = Position(0f, 0f),
        )

        children.fold(innerContext) { c, child ->
            c.startPosition.x = 0f
            val pos = child.endPosition(c)
            xMax = max(xMax, pos.x)
            yMax = max(yMax, pos.y)
            println("block yMax = $yMax")
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