package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.multipdf.LayerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.awt.geom.AffineTransform

fun document(
    pageTemplate: PageTemplate,
    builder: DocumentBuilder.() -> Unit,
): PDDocument {

    val templateDoc = Loader.loadPDF(pageTemplate.bytes)
    val templatePage = templateDoc.getPage(0)
    val format = templatePage.mediaBox
    val doc = document(format, pageTemplate.pageMargin, builder)

    val pages = doc.pages

    val layerUtil = LayerUtility(doc)
    val templateForm = layerUtil.importPageAsForm(templateDoc, templatePage)
    val transform = AffineTransform()

    pages.forEachIndexed { i, page ->
        layerUtil.wrapInSaveRestore(page)
        layerUtil.appendFormAsLayer(page, templateForm, transform, "template-layer-$i")
    }

    templateDoc.close()

    return doc
}

fun document(
    format: PDRectangle = PDRectangle.A4,
    pageMargin: PageMargin = PageMargin.default,
    builder: DocumentBuilder.() -> Unit,
): PDDocument {

    val pages = DocumentBuilder(format, pageMargin).apply(builder).pages

    return Document(
        pages = pages,
    ).render()
}
