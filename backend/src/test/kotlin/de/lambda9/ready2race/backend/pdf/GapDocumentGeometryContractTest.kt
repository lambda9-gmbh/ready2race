package de.lambda9.ready2race.backend.pdf

import de.lambda9.ready2race.backend.docx.DocxPageSize
import de.lambda9.ready2race.backend.docx.gapDocumentsDocx
import de.lambda9.ready2race.backend.text.TextAlign
import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.cos.COSNumber
import org.apache.pdfbox.pdfparser.PDFStreamParser
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Die senkrechte Zentrierung eines Platzhalters (siehe [GapTextMetrics.blockTop] in AdditionalText.kt)
 * ist der eine Vertrag, der PDF und DOCX auf denselben Punkt des vorgedruckten Papiers treffen lässt.
 * Vor der Zusammenführung stand dieselbe Formel zweimal, unabhängig herbeigerechnet, in zwei
 * Koordinatensystemen - jede Testsuite prüfte nur ihre eigenen, von Hand berechneten Konstanten, sodass
 * eine Änderung an einer Formel die andere unbemerkt hätte auseinanderlaufen lassen können.
 *
 * Dieser Test rendert dieselbe [AdditionalText] durch beide Renderer und vergleicht die tatsächlich
 * erzeugte senkrechte Position der ersten Zeile - top-down, in Twips (Word rechnet in Twips, daher die
 * Toleranz von einem Twip für Rundung).
 */
class GapDocumentGeometryContractTest {

    private fun blankA4Template(): ByteArray {
        val doc = PDDocument()
        doc.addPage(PDPage(PDRectangle.A4))
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun addition(content: String, fontSize: Float?) = AdditionalText(
        content = content,
        page = 1,
        relLeft = 0.0,
        relTop = 0.45,
        relWidth = 1.0,
        relHeight = 0.05,
        textAlign = TextAlign.CENTER,
        fontSize = fontSize,
    )

    /** ty-Komponente (Baseline) der ersten Tm-Textmatrix auf Seite 1 - das ist Zeile 0. */
    private fun firstLineBaseline(doc: PDDocument): Float {
        val tokens = PDFStreamParser(doc.getPage(0)).parse()
        var pending = mutableListOf<Any>()
        for (token in tokens) {
            if (token is Operator) {
                if (token.name == "Tm") {
                    return (pending[5] as COSNumber).floatValue()
                }
                pending = mutableListOf()
            } else {
                pending.add(token)
            }
        }
        error("Kein Tm-Operator gefunden")
    }

    /**
     * Top-down-Position der Blockoberkante in Twips, aus der tatsächlich gerenderten PDF-Seite
     * zurückgerechnet: Baseline der ersten Zeile -> Blockoberkante bottom-up (Umkehrung der Formel aus
     * drawAddition) -> top-down (Seitenhöhe minus bottom-up-Wert) -> Twips.
     */
    private fun pdfBlockTopTwips(addition: AdditionalText): Long {
        val doc = gapDocuments(
            template = blankA4Template(),
            font = null,
            withBackground = false,
            pages = listOf(listOf(addition)),
        )
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val pageHeight = PDRectangle.A4.height
        val boxHeight = pageHeight * addition.relHeight.toFloat()
        val fontSize = addition.fontSize ?: boxHeight
        val lineHeight = fontSize * 1.2f
        val capHeight = fontSize * font.fontDescriptor.capHeight / 1000

        val baseline = firstLineBaseline(doc)
        // baseline = blockTop - lineHeight * 0 - (lineHeight + capHeight) / 2, siehe drawAddition.
        val blockTopBottomUp = baseline + (lineHeight + capHeight) / 2
        val blockTopTopDown = pageHeight - blockTopBottomUp
        doc.close()
        return (blockTopTopDown * 20f).roundToLong()
    }

    private fun docxBlockTopTwips(addition: AdditionalText): Long {
        val document = gapDocumentsDocx(
            templatePageSizes = listOf(DocxPageSize(PDRectangle.A4.width, PDRectangle.A4.height)),
            fontName = null,
            certificates = listOf(listOf(addition)),
        )
        val frame = document.paragraphs.first { it.ctp.pPr?.framePr != null }.ctp.pPr.framePr
        val y = frame.y.toString().toLong()
        document.close()
        return y
    }

    private fun assertSamePosition(addition: AdditionalText) {
        val pdfTwips = pdfBlockTopTwips(addition)
        val docxTwips = docxBlockTopTwips(addition)
        assertTrue(
            abs(pdfTwips - docxTwips) <= 1,
            "PDF blockTop=$pdfTwips Twips, DOCX blockTop=$docxTwips Twips - weichen um mehr als ein Twip ab",
        )
    }

    @Test
    fun singleLinePlaceholderLandsOnTheSameSpotInBothFormats() {
        assertSamePosition(addition("1. Platz", fontSize = 20f))
    }

    @Test
    fun multiLinePlaceholderLandsOnTheSameSpotInBothFormats() {
        assertSamePosition(addition("Carina Hein\nMalte Hein", fontSize = 20f))
    }

    @Test
    fun placeholderWithoutExplicitFontSizeLandsOnTheSameSpotInBothFormats() {
        assertSamePosition(addition("1. Platz", fontSize = null))
    }
}
