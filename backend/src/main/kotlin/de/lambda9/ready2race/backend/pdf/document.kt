package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.pdmodel.common.PDRectangle

fun document(
    pageTemplate: PageTemplate? = null,
    builder: DocumentBuilder.() -> Unit,
): Document {

    val pages = DocumentBuilder(pageTemplate).apply(builder).pages

    return Document(
        pageTemplate = pageTemplate,
        pages = pages
    )
}
