package de.lambda9.ready2race.backend.pdf

data class Padding(
    val top: Float,
    val left: Float,
    val right: Float,
    val bottom: Float,
) {

    constructor(padding: Float) : this(padding, padding, padding, padding)
    constructor(x: Float, y: Float) : this(y, x, x, y)

    val x get() = left + right
    val y get() = top + bottom

    operator fun plus(other: Padding): Padding {
        return Padding(
            top = top + other.top,
            left = left + other.left,
            right = right + other.right,
            bottom = bottom + other.bottom,
        )
    }

    companion object {

        val defaultPagePadding
            get() = Padding(
                30F, 40F, 40F, 50F,
            )
    }
}
