package de.lambda9.ready2race.backend.pdf

data class PageMargin(
    val pageMarginTop: Float,
    val pageMarginLeft: Float,
    val pageMarginRight: Float,
    val pageMarginBottom: Float,
) {
    companion object {

        val default get() = PageMargin(
            pageMarginTop = 10F,
            pageMarginLeft = 10F,
            pageMarginRight = 10F,
            pageMarginBottom = 10F,
        )
    }
}
