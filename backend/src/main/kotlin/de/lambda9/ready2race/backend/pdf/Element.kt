package de.lambda9.ready2race.backend.pdf

interface Element {

    val padding: Padding

    fun getXMin(context: RenderContext): Float = context.parentsPadding.left + padding.left
    fun getYMin(context: RenderContext): Float = context.page.mediaBox.height - context.parentsPadding.top - padding.top

    fun getXMax(context: RenderContext): Float = context.page.mediaBox.width - context.parentsPadding.right + padding.right
    fun getYMax(context: RenderContext): Float = context.parentsPadding.bottom + padding.bottom

    fun render(context: RenderContext, requestNewPage: (currentContext: RenderContext) -> RenderContext): RenderContext
    fun endPosition(context: SizeContext): Position
}