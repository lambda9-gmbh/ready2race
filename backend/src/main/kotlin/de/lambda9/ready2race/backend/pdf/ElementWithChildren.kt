package de.lambda9.ready2race.backend.pdf

import kotlin.math.max

interface ElementWithChildren<T : Element> : Element {

    val children: List<T>

    override fun endPosition(position: Position): Position {
        var xMax = 0F
        var yMax = 0F

        val lastPosition = children.fold(position) { p, child ->
            xMax = max(xMax, p.x)
            yMax = max(yMax, p.y)
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