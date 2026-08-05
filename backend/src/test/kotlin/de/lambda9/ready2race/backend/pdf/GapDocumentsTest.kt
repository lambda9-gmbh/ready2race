package de.lambda9.ready2race.backend.pdf

import de.lambda9.ready2race.backend.text.TextAlign
import org.apache.pdfbox.Loader
import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.cos.COSNumber
import org.apache.pdfbox.pdfparser.PDFStreamParser
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GapDocumentsTest {

    /** Einseitige A4-Vorlage mit einem erkennbaren Text, der nur aus dem Design stammt. */
    private fun templateBytes(): ByteArray {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val content = PDPageContentStream(doc, page)
        content.beginText()
        content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
        content.newLineAtOffset(50f, 50f)
        content.showText("DESIGN")
        content.endText()
        content.close()

        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun addition(content: String, relTop: Double, fontSize: Float? = 20f) = AdditionalText(
        content = content,
        page = 1,
        relLeft = 0.0,
        relTop = relTop,
        relWidth = 1.0,
        relHeight = 0.05,
        textAlign = TextAlign.CENTER,
        fontSize = fontSize,
    )

    private fun text(doc: PDDocument, page: Int): String {
        val stripper = PDFTextStripper()
        stripper.startPage = page
        stripper.endPage = page
        return stripper.getText(doc)
    }

    /**
     * Echte Schrift-Bytes, ohne eine zusätzliche Datei ins Repo zu legen: PDFBox bringt in seinem
     * eigenen Jar eine TTF-Testressource mit, die auf dem Test-Classpath liegt.
     */
    private fun embeddedFontBytes(): ByteArray =
        PDDocument::class.java.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")!!
            .use { it.readBytes() }

    /** Zerlegt den Content-Stream einer Seite in (Operator-Name, davor gelesene Operanden)-Paare. */
    private fun operatorOperands(page: PDPage): List<Pair<String, List<Any>>> {
        val tokens = PDFStreamParser(page).parse()
        val result = mutableListOf<Pair<String, List<Any>>>()
        var pending = mutableListOf<Any>()
        for (token in tokens) {
            if (token is Operator) {
                result.add(token.name to pending.toList())
                pending = mutableListOf()
            } else {
                pending.add(token)
            }
        }
        return result
    }

    private fun Any.numberValue(): Float = (this as COSNumber).floatValue()

    /**
     * Speichert und lädt neu, wie es jeder echte Verbraucher der erzeugten PDDocument auch tut. Bei
     * eingebetteten Type0-Schriften schließt PDFBox das Subset-Embedding (inkl. ToUnicode-CMap) erst
     * beim Speichern ab; liest man vorher aus den Resources oder extrahiert Text, fällt PDFBox auf
     * eine Font-Substitution zurück und loggt eine WARNUNG, obwohl in der echten Nutzung immer erst
     * gespeichert wird.
     */
    private fun saveAndReload(doc: PDDocument): PDDocument {
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return Loader.loadPDF(out.toByteArray())
    }

    private fun textAfterSaveAndReload(doc: PDDocument, page: Int): String {
        val reloaded = saveAndReload(doc)
        val result = text(reloaded, page)
        reloaded.close()
        return result
    }

    @Test
    fun onePagePerCertificate() {
        val doc = gapDocuments(
            template = templateBytes(),
            font = null,
            withBackground = false,
            pages = listOf(
                listOf(addition("1. Platz", 0.45), addition("Carina Hein", 0.5)),
                listOf(addition("2. Platz", 0.45), addition("Malte Hein", 0.5)),
            ),
        )

        assertEquals(2, doc.numberOfPages)
        assertTrue(text(doc, 1).contains("Carina Hein"))
        assertTrue(text(doc, 2).contains("Malte Hein"))
        doc.close()
    }

    @Test
    fun withoutBackgroundTheDesignIsAbsent() {
        val doc = gapDocuments(
            template = templateBytes(),
            font = null,
            withBackground = false,
            pages = listOf(listOf(addition("1. Platz", 0.45))),
        )

        assertFalse(text(doc, 1).contains("DESIGN"))
        assertTrue(text(doc, 1).contains("1. Platz"))
        doc.close()
    }

    @Test
    fun withBackgroundTheDesignIsPresent() {
        val doc = gapDocuments(
            template = templateBytes(),
            font = null,
            withBackground = true,
            pages = listOf(listOf(addition("1. Platz", 0.45))),
        )

        val content = text(doc, 1)
        assertTrue(content.contains("DESIGN"))
        assertTrue(content.contains("1. Platz"))
        doc.close()
    }

    @Test
    fun pageFormatMatchesTheTemplate() {
        val doc = gapDocuments(
            template = templateBytes(),
            font = null,
            withBackground = false,
            pages = listOf(listOf(addition("1. Platz", 0.45))),
        )

        assertEquals(PDRectangle.A4.width, doc.getPage(0).mediaBox.width)
        assertEquals(PDRectangle.A4.height, doc.getPage(0).mediaBox.height)
        doc.close()
    }

    @Test
    fun multipleLinesAreRenderedSeparately() {
        val doc = gapDocuments(
            template = templateBytes(),
            font = null,
            withBackground = false,
            pages = listOf(listOf(addition("Carina Hein\nMalte Hein", 0.45))),
        )

        val lines = text(doc, 1).lines().filter { it.isNotBlank() }
        assertEquals(listOf("Carina Hein", "Malte Hein"), lines)
        doc.close()
    }

    @Test
    fun boldAndItalicDoNotBreakRendering() {
        val doc = gapDocuments(
            template = templateBytes(),
            font = null,
            withBackground = false,
            pages = listOf(
                listOf(
                    addition("1. Platz", 0.45).copy(bold = true),
                    addition("33:17,7 min", 0.55).copy(italic = true),
                )
            ),
        )

        val content = text(doc, 1)
        assertTrue(content.contains("1. Platz"))
        assertTrue(content.contains("33:17,7 min"))
        doc.close()
    }

    @Test
    fun embeddedFontIsUsedInsteadOfHelvetica() {
        // Mit übergebener Schriftdatei muss die Seite die eingebettete Schrift nutzen, nicht Helvetica.
        val doc = gapDocuments(
            template = templateBytes(),
            font = embeddedFontBytes(),
            withBackground = false,
            pages = listOf(listOf(addition("1. Platz", 0.45))),
        )

        val reloaded = saveAndReload(doc)
        val page = reloaded.getPage(0)
        val fontName = page.resources.getFont(page.resources.fontNames.first()).name
        assertFalse(fontName.contains("Helvetica", ignoreCase = true))
        assertTrue(fontName.contains("Liberation", ignoreCase = true))
        reloaded.close()
    }

    @Test
    fun boldWithEmbeddedFontUsesSyntheticFillStrokeRenderingMode() {
        // Ohne echten Fett-Schnitt wird Fett über den Textrendermodus FILL_STROKE plus Randlinie simuliert.
        val doc = gapDocuments(
            template = templateBytes(),
            font = embeddedFontBytes(),
            withBackground = false,
            pages = listOf(listOf(addition("1. Platz", 0.45).copy(bold = true))),
        )

        val operators = operatorOperands(doc.getPage(0))
        val renderingMode = operators.first { it.first == "Tr" }.second.single().numberValue()
        assertEquals(2f, renderingMode) // FILL_STROKE

        val lineWidth = operators.first { it.first == "w" }.second.single().numberValue()
        assertTrue(lineWidth > 0f)
        doc.close()
    }

    @Test
    fun italicWithEmbeddedFontShearsTheTextMatrix() {
        // Ohne echten Kursiv-Schnitt wird Kursiv über eine Schrägstellung der Textmatrix simuliert.
        val doc = gapDocuments(
            template = templateBytes(),
            font = embeddedFontBytes(),
            withBackground = false,
            pages = listOf(listOf(addition("1. Platz", 0.45).copy(italic = true))),
        )

        val tm = operatorOperands(doc.getPage(0)).first { it.first == "Tm" }.second
        assertEquals(6, tm.size)
        assertNotEquals(0f, tm[2].numberValue()) // c-Komponente der Matrix trägt die Schräge
        doc.close()
    }

    @Test
    fun boldWithoutFontUsesRealBoldCutInsteadOfSyntheticStroke() {
        // Gegenprobe zu den beiden Tests oben: ohne Schriftdatei gibt es einen echten Fett-Schnitt
        // (HELVETICA_BOLD), daher wird kein Tr-Operator für FILL_STROKE geschrieben.
        val doc = gapDocuments(
            template = templateBytes(),
            font = null,
            withBackground = false,
            pages = listOf(listOf(addition("1. Platz", 0.45).copy(bold = true))),
        )

        assertFalse(operatorOperands(doc.getPage(0)).any { it.first == "Tr" })
        doc.close()
    }

    @Test
    fun textWithEmbeddedFontStillExtracts() {
        // Die eingebettete Schrift darf die Textextraktion nicht kaputt machen.
        val doc = gapDocuments(
            template = templateBytes(),
            font = embeddedFontBytes(),
            withBackground = false,
            pages = listOf(listOf(addition("Carina Hein", 0.45))),
        )

        assertTrue(textAfterSaveAndReload(doc, 1).contains("Carina Hein"))
    }

    /** Ob [font] jedes Zeichen von [text] einzeln kodieren kann - unabhängig von sanitizeForFont selbst. */
    private fun canEncodeEveryCharacter(text: String, font: org.apache.pdfbox.pdmodel.font.PDFont): Boolean =
        text.codePoints().toArray().all { codePoint ->
            try {
                font.encode(String(Character.toChars(codePoint)))
                true
            } catch (ex: Exception) {
                false
            }
        }

    @Test
    fun sanitizeForFontMakesAPolishClubNameEncodableByHelvetica() {
        // Helvetica (WinAnsi) kann Ł/ź nicht kodieren; die NFD-Zerlegung entfernt das Kombinationszeichen
        // von ź (-> z), Ł hat keine Zerlegung und wird zu '?'. Das Ergebnis muss vollständig kodierbar sein.
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val sanitized = "AZS Łódź".sanitizeForFont(font)

        assertTrue(sanitized.isNotBlank())
        assertTrue(canEncodeEveryCharacter(sanitized, font))
    }

    @Test
    fun sanitizeForFontLeavesAlreadyEncodableTextUnchanged() {
        // Text, der ausschließlich aus WinAnsi-Zeichen besteht, darf sich zeichenweise nicht ändern -
        // sonst würde sich die heute schon funktionierende Teilnahmeurkunde verändern.
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val text = "Ruderklub Flensburg von 1877 é à ü"

        assertEquals(text, text.sanitizeForFont(font))
    }

    @Test
    fun foreignClubNameDoesNotBreakTheWholeBatch() {
        // Regressionstest für den Finding: ein Clubname mit Zeichen, die Helvetica (Default-Schrift,
        // keine hochgeladene Vorlagenschrift) nicht kodieren kann, darf den PDF-Export nicht mit einer
        // IllegalArgumentException abbrechen - weder beim Messen (getStringWidth) noch beim Zeichnen
        // (showText). Enthält Latein-Erweitert (ź), einen Cyrillic-Buchstaben (М) und einen CJK-Block.
        val doc = gapDocuments(
            template = templateBytes(),
            font = null,
            withBackground = false,
            pages = listOf(listOf(addition("AZS Łódź – Команда М – 東京クラブ", 0.45))),
        )

        val content = textAfterSaveAndReload(doc, 1)
        assertTrue(content.isNotBlank())
    }

    @Test
    fun existingSingleDocumentApiStillWorks() {
        // Rückwärtskompatibilität: die Teilnahmeurkunde nutzt weiterhin document(original, additions)
        // und erwartet das Design auf der Seite.
        val doc = document(
            original = templateBytes(),
            additions = listOf(addition("Max Mustermann", 0.45, fontSize = null)),
        )

        assertEquals(1, doc.numberOfPages)
        val content = text(doc, 1)
        assertTrue(content.contains("DESIGN"))
        assertTrue(content.contains("Max Mustermann"))
        doc.close()
    }
}
