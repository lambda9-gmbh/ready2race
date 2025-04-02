package de.lambda9.ready2race.backend.pdf

interface Element {

    val padding: Padding

    fun getX0(context: RenderContext): Float = context.parentsPadding.left + padding.left
    fun getY0(context: RenderContext): Float = context.page.mediaBox.height - context.parentsPadding.top - padding.top

    fun render(context: RenderContext, requestNewPage: (currentContext: RenderContext) -> RenderContext): RenderContext
    fun endPosition(position: Position): Position
}