package de.lambda9.ready2race.backend.pdf

import de.lambda9.ready2race.backend.text.sanitizeNonPrintable
import org.apache.pdfbox.Loader
import org.apache.pdfbox.multipdf.LayerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.awt.Color
import java.awt.geom.AffineTransform

fun document(
    original: ByteArray,
    additions: List<AdditionalText>,
): PDDocument {

    val pdf = Loader.loadPDF(original)

    additions.forEach { addition ->

        if (addition.page > pdf.numberOfPages) {
            return@forEach
        }

        val page = pdf.getPage(addition.page - 1)
        val content = PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)

        val w = (page.mediaBox.width * addition.relWidth).toFloat()
        val h = (page.mediaBox.height * addition.relHeight).toFloat()
        val x = (page.mediaBox.width * addition.relLeft).toFloat()
        val y = (page.mediaBox.height * (1 - addition.relTop) - h).toFloat()

        val fontSize = h

        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)

        val text = addition.content.sanitizeNonPrintable()

        content.setFont(font, fontSize)
        content.setNonStrokingColor(Color.DARK_GRAY)

        val textWidth = font.getStringWidth(text) / 1000 * fontSize
        val xOffset = when (addition.textAlign) {
            de.lambda9.ready2race.backend.text.TextAlign.LEFT -> x
            de.lambda9.ready2race.backend.text.TextAlign.CENTER -> x + (w - textWidth) / 2
            de.lambda9.ready2race.backend.text.TextAlign.RIGHT -> x + w - textWidth
        }

        content.beginText()
        content.newLineAtOffset(
            xOffset,
            y + h * 0.5f - fontSize * font.fontDescriptor.capHeight / 1000 / 2,
        )
        content.showText(text)
        content.endText()
        content.close()
    }

    return pdf
}

fun document(
    pageTemplate: PageTemplate?,
    builder: DocumentBuilder.() -> Unit,
): PDDocument {

    if (pageTemplate == null) {
        return document(builder = builder)
    }

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

    return resultDoc
}

fun document(
    format: PDRectangle = PDRectangle.A4,
    pagePadding: Padding = Padding.defaultPagePadding,
    builder: DocumentBuilder.() -> Unit,
): PDDocument {

    val pages = DocumentBuilder(format, pagePadding).apply(builder).pages

    return Document(
        pages = pages,
    ).render()
}
