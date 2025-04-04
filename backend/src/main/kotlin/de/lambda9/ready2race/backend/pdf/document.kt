package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.multipdf.LayerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.awt.geom.AffineTransform

fun document(
    pageTemplate: PageTemplate,
    builder: DocumentBuilder.() -> Unit,
): PDDocument {

    val templateDoc = Loader.loadPDF(pageTemplate.bytes)
    val templatePage = templateDoc.getPage(0)
    val format = templatePage.mediaBox
    val doc = document(format, pageTemplate.pagepadding, builder)

    val pages = doc.pages

    val resultDoc = PDDocument()
    val layerUtil = LayerUtility(resultDoc)
    val templateForm = layerUtil.importPageAsForm(templateDoc, templatePage)
    val transform = AffineTransform()

    pages.forEachIndexed { i, page ->
        val resultPage = PDPage(format)
        resultDoc.addPage(resultPage)

        val pageForm = layerUtil.importPageAsForm(doc, page)

        layerUtil.appendFormAsLayer(resultPage, templateForm, transform, "template-layer-$i")
        layerUtil.appendFormAsLayer(resultPage, pageForm, transform, "page-layer-$i")
    }

    templateDoc.close()

    return doc
}

fun document(
    format: PDRectangle = PDRectangle.A4,
    pagepadding: Padding = Padding.defaultPagePadding,
    builder: DocumentBuilder.() -> Unit,
): PDDocument {

    val pages = DocumentBuilder(format, pagepadding).apply(builder).pages

    return Document(
        pages = pages,
    ).render()
}
