package de.lambda9.ready2race.backend.pdf

data class Padding(
    val top: Float = 0F,
    val left: Float = 0F,
    val right: Float = 0F,
    val bottom: Float = 0F,
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

        fun fromMillimetersOrDefault(
            top: Float? = null,
            left: Float? = null,
            right: Float? = null,
            bottom: Float? = null,
        ): Padding = Padding(
            top = top?.let { it * POINTS_PER_MM } ?: defaultPagePadding.top,
            left = left?.let { it * POINTS_PER_MM } ?: defaultPagePadding.left,
            right = right?.let { it * POINTS_PER_MM } ?: defaultPagePadding.right,
            bottom = bottom?.let { it * POINTS_PER_MM } ?: defaultPagePadding.bottom,
        )

        val defaultPagePadding
            get() = Padding(
                25 * POINTS_PER_MM,
                25 * POINTS_PER_MM,
                20 * POINTS_PER_MM,
                25 * POINTS_PER_MM,
            )
    }
}
