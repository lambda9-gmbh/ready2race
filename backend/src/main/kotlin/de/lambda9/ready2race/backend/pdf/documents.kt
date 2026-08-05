package de.lambda9.ready2race.backend.pdf

import de.lambda9.ready2race.backend.text.TextAlign
import de.lambda9.ready2race.backend.text.sanitizeNonPrintable
import org.apache.pdfbox.Loader
import org.apache.pdfbox.multipdf.LayerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode
import org.apache.pdfbox.util.Matrix
import java.awt.Color
import java.awt.geom.AffineTransform
import java.io.IOException
import java.text.Normalizer

private class GapFonts(
    val regular: PDFont,
    val bold: PDFont,
    val italic: PDFont,
    val boldItalic: PDFont,
    /** Bei einer eingebetteten Vorlagenschrift gibt es nur einen Schnitt; Fett und Kursiv werden simuliert. */
    val synthesizeStyles: Boolean,
) {
    fun forStyle(bold: Boolean, italic: Boolean): PDFont = when {
        bold && italic -> boldItalic
        bold -> this.bold
        italic -> this.italic
        else -> regular
    }

    companion object {
        fun load(doc: PDDocument, font: ByteArray?): GapFonts {
            if (font != null) {
                val embedded = PDType0Font.load(doc, font.inputStream())
                return GapFonts(embedded, embedded, embedded, embedded, synthesizeStyles = true)
            }

            return GapFonts(
                regular = PDType1Font(Standard14Fonts.FontName.HELVETICA),
                bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                italic = PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE),
                boldItalic = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE),
                synthesizeStyles = false,
            )
        }
    }
}

/**
 * Ersetzt Zeichen, die [font] nicht kodieren kann. Für jedes nicht kodierbare Zeichen wird zunächst
 * die Unicode-NFD-Zerlegung ohne Kombinationszeichen versucht (ř -> r, é -> e); bleibt das Ergebnis
 * weiterhin nicht kodierbar, tritt ein '?' an dessen Stelle. Kodierbare Zeichen durchlaufen die
 * Funktion unverändert - das ist entscheidend, weil dieselbe Funktion auch die heute schon
 * funktionierende Teilnahmeurkunde durchläuft (document(original, additions)) und deren Ausgabe
 * unverändert bleiben muss.
 *
 * Ohne das würde ein einziger nicht kodierbarer Vereins- oder Ortsname (z. B. ein polnischer oder
 * tschechischer Clubname bei einer Küstenregatta) den gesamten Urkunden-Export einer Veranstaltung
 * mit einer untypisierten IllegalArgumentException abbrechen, weil `font.getStringWidth`/
 * `content.showText` das Zeichen nicht kodieren können.
 */
fun String.sanitizeForFont(font: PDFont): String = buildString {
    this@sanitizeForFont.codePoints().forEach { codePoint ->
        val original = String(Character.toChars(codePoint))
        if (font.canEncodeSafely(original)) {
            append(original)
        } else {
            val decomposed = Normalizer.normalize(original, Normalizer.Form.NFD)
                .filter { !it.isCombiningMark() }
            if (decomposed.isNotEmpty() && decomposed.all { font.canEncodeSafely(it.toString()) }) {
                append(decomposed)
            } else {
                append('?')
            }
        }
    }
}

private fun Char.isCombiningMark(): Boolean = when (Character.getType(this)) {
    Character.NON_SPACING_MARK.toInt(),
    Character.COMBINING_SPACING_MARK.toInt(),
    Character.ENCLOSING_MARK.toInt(),
    -> true

    else -> false
}

private fun PDFont.canEncodeSafely(text: String): Boolean = try {
    encode(text)
    true
} catch (ex: IllegalArgumentException) {
    false
} catch (ex: IOException) {
    false
}

private fun drawAddition(
    doc: PDDocument,
    page: PDPage,
    addition: AdditionalText,
    fonts: GapFonts,
) {
    val content = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)

    val w = (page.mediaBox.width * addition.relWidth).toFloat()
    val h = (page.mediaBox.height * addition.relHeight).toFloat()
    val x = (page.mediaBox.width * addition.relLeft).toFloat()
    val y = (page.mediaBox.height * (1 - addition.relTop) - h).toFloat()

    val font = fonts.forStyle(addition.bold, addition.italic)
    val lines = addition.content.split("\n").map { it.sanitizeNonPrintable().sanitizeForFont(font) }

    val metrics = addition.gapTextMetrics(h, lines.size)
    val fontSize = metrics.fontSize

    content.setFont(font, fontSize)
    content.setNonStrokingColor(Color.DARK_GRAY)

    // Ohne echte Schnitte werden Fett und Kursiv nachgebildet: Fett über einen dünnen Rand um die
    // Glyphen, Kursiv über eine Schrägstellung der Textmatrix.
    val synthesizeBold = fonts.synthesizeStyles && addition.bold
    val shear = if (fonts.synthesizeStyles && addition.italic) 0.25f else 0f

    if (synthesizeBold) {
        content.setRenderingMode(RenderingMode.FILL_STROKE)
        content.setStrokingColor(Color.DARK_GRAY)
        content.setLineWidth(fontSize * 0.02f)
    }

    val capHeight = fontSize * font.fontDescriptor.capHeight / 1000
    val lineHeight = metrics.lineHeight
    // GapTextMetrics.blockTop ist top-down definiert (Versatz von der Kastenoberkante); PDF rechnet
    // bottom-up, daher die einmalige Umrechnung: Kastenoberkante in PDF-Koordinaten (y + h) minus
    // dem top-down-Versatz. Das ist rechnerisch identisch zur vorherigen, direkt hier stehenden
    // Formel `y + h / 2 + lineHeight * lines.size / 2`.
    val blockTop = (y + h) - metrics.blockTop

    lines.forEachIndexed { index, line ->
        val textWidth = font.getStringWidth(line) / 1000 * fontSize
        val xOffset = when (addition.textAlign) {
            TextAlign.LEFT -> x
            TextAlign.CENTER -> x + (w - textWidth) / 2
            TextAlign.RIGHT -> x + w - textWidth
        }
        val baseline = blockTop - lineHeight * index - (lineHeight + capHeight) / 2

        content.beginText()
        content.setTextMatrix(Matrix(1f, 0f, shear, 1f, xOffset, baseline))
        content.showText(line)
        content.endText()
    }

    content.close()
}

/**
 * Erzeugt eine Serie: eine Seite je Eintrag in [pages], im Seitenformat der Vorlage.
 *
 * @param withBackground legt die Vorlagenseite als Layer unter den Text. Für den Druck auf
 * vorgedrucktes Papier bleibt das aus, sonst läge das Design doppelt auf dem Blatt.
 * @param font optionale Schriftdatei (TTF/OTF), die eingebettet wird; ohne sie wird Helvetica genutzt.
 */
fun gapDocuments(
    template: ByteArray,
    font: ByteArray?,
    withBackground: Boolean,
    pages: List<List<AdditionalText>>,
): PDDocument = Loader.loadPDF(template).use { templateDoc ->
    val templatePage = templateDoc.getPage(0)
    val format = templatePage.mediaBox

    val result = PDDocument()
    val fonts = GapFonts.load(result, font)

    val layerUtil = if (withBackground) LayerUtility(result) else null
    val templateForm = layerUtil?.importPageAsForm(templateDoc, templatePage)

    pages.forEachIndexed { index, additions ->
        val page = PDPage(format)
        result.addPage(page)

        if (layerUtil != null && templateForm != null) {
            layerUtil.appendFormAsLayer(page, templateForm, AffineTransform(), "template-layer-$index")
        }

        additions.filter { it.page == 1 }.forEach { drawAddition(result, page, it, fonts) }
    }

    result
}

/**
 * Befüllt die Vorlage selbst — eine Urkunde, Design inklusive. Wird von der Teilnahmeurkunde genutzt.
 */
fun document(
    original: ByteArray,
    additions: List<AdditionalText>,
): PDDocument {

    val pdf = Loader.loadPDF(original)
    val fonts = GapFonts.load(pdf, null)

    additions.forEach { addition ->
        if (addition.page > pdf.numberOfPages) {
            return@forEach
        }
        drawAddition(pdf, pdf.getPage(addition.page - 1), addition, fonts)
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
