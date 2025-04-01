package de.lambda9.ready2race.backend.pdf

import de.lambda9.ready2race.backend.pdf.elements.text.Text

class PageBuilder {

    internal val elements: MutableList<Element> = mutableListOf()

    fun text(content: String) {
        elements.add(Text(content))
    }
}