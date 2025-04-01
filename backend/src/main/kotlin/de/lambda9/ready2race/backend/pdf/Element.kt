package de.lambda9.ready2race.backend.pdf

interface Element {

    fun render(context: RenderContext, requestNewPage: (currentContext: RenderContext) -> RenderContext): RenderContext
}