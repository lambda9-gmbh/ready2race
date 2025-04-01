package de.lambda9.ready2race.backend.pdf

class DocumentBuilder(
    private val pageTemplate: PageTemplate?
) {

    internal val pages: MutableList<Page> = mutableListOf()

    fun page(
        builder: PageBuilder.() -> Unit = {}
    ) {
        val elements = PageBuilder().apply(builder).elements
        pages.add(Page(pageTemplate, elements))
    }

}