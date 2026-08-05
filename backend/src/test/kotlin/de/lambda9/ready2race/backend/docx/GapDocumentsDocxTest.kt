package de.lambda9.ready2race.backend.docx

import de.lambda9.ready2race.backend.pdf.AdditionalText
import de.lambda9.ready2race.backend.text.TextAlign
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.pdfbox.pdmodel.common.PDRectangle
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GapDocumentsDocxTest {

    private val a4 = DocxPageSize(PDRectangle.A4.width, PDRectangle.A4.height)

    private fun addition(
        content: String,
        relTop: Double,
        page: Int = 1,
        bold: Boolean = false,
        italic: Boolean = false,
    ) = AdditionalText(
        content = content,
        page = page,
        relLeft = 0.0,
        relTop = relTop,
        relWidth = 1.0,
        relHeight = 0.05,
        textAlign = TextAlign.CENTER,
        fontSize = 20f,
        bold = bold,
        italic = italic,
    )

    /** Eine Vorlagenseite (A4), beliebig viele Urkunden - das Verhalten der Siegerurkunde. */
    private fun doc(certificates: List<List<AdditionalText>>) = gapDocumentsDocx(
        templatePageSizes = listOf(a4),
        fontName = "TheSansOffice",
        certificates = certificates,
    )

    @Test
    fun pageSizeIsTakenFromTheTemplateInTwips() {
        val document = doc(listOf(listOf(addition("1. Platz", 0.45))))
        val pgSz = document.document.body.sectPr.pgSz

        // A4 = 595.27563 x 841.8898 pt, 1 pt = 20 twips, gerundet: 11906 x 16838
        assertEquals(11906L, pgSz.w.toString().toLong())
        assertEquals(16838L, pgSz.h.toString().toLong())
        document.close()
    }

    @Test
    fun everyPlaceholderBecomesAFramedParagraph() {
        val document = doc(listOf(listOf(addition("1. Platz", 0.45), addition("Carina Hein", 0.5))))

        val framed = document.paragraphs.filter { it.ctp.pPr?.framePr != null }
        assertEquals(2, framed.size)
        assertEquals(listOf("1. Platz", "Carina Hein"), framed.map { it.text })
        document.close()
    }

    @Test
    fun frameIsAnchoredToThePageAtTheRelativePosition() {
        val document = doc(listOf(listOf(addition("1. Platz", 0.45))))

        val frame = document.paragraphs.first { it.ctp.pPr?.framePr != null }.ctp.pPr.framePr
        assertNotNull(frame)
        // Kastenoberkante = 0.45 * 841.8898 = 378.85 pt, Kastenhöhe = 0.05 * 841.8898 = 42.09 pt,
        // Zeilenhöhe = 20 pt * 1.2 = 24 pt. Eine Zeile, senkrecht zentriert:
        // 378.85 + (42.09 - 24) / 2 = 387.90 pt -> 7758 Twips. Rahmenhöhe = 24 pt -> 480 Twips.
        assertEquals(0L, frame.x.toString().toLong())
        assertEquals(7758L, frame.y.toString().toLong())
        assertEquals(11906L, frame.w.toString().toLong())
        assertEquals(480L, frame.h.toString().toLong())
        document.close()
    }

    @Test
    fun runCarriesFontNameSizeAndStyle() {
        val document = doc(listOf(listOf(addition("1. Platz", 0.45, bold = true, italic = true))))

        val run = document.paragraphs.first { it.ctp.pPr?.framePr != null }.runs.first()
        assertEquals("TheSansOffice", run.fontFamily)
        assertEquals(20, run.fontSize)
        assertTrue(run.isBold)
        assertTrue(run.isItalic)
        document.close()
    }

    @Test
    fun fontSizeFallsBackToTheBoxHeightWhenNotConfigured() {
        // Ohne konfigurierte Schriftgröße gilt dieselbe Regel wie im PDF-Renderer (drawAddition):
        // die Schriftgröße entspricht der Kastenhöhe. Kastenhöhe = 0.05 * 841.8898 pt ≈ 42.09 pt.
        // Das muss sowohl für den sichtbaren Textlauf als auch für die Rahmenhöhe gelten, sonst
        // driftet die Word-Ausgabe gegenüber der PDF-Ausgabe ab.
        val document = doc(listOf(listOf(addition("1. Platz", 0.45).copy(fontSize = null))))

        val paragraph = document.paragraphs.first { it.ctp.pPr?.framePr != null }
        val run = paragraph.runs.first()

        val boxHeight = PDRectangle.A4.height * 0.05f
        val expectedFontSize = boxHeight.roundToInt()
        val expectedLineHeightTwips = (boxHeight * 1.2f * 20f).roundToLong()

        // POI liefert -1, wenn keine Schriftgröße auf dem Run gesetzt wurde - das ist der Fehlerfall.
        assertNotEquals(-1, run.fontSize)
        assertEquals(expectedFontSize, run.fontSize)
        assertEquals(expectedLineHeightTwips, paragraph.ctp.pPr.framePr.h.toString().toLong())
        document.close()
    }

    @Test
    fun alignmentIsTakenFromTextAlign() {
        val document = gapDocumentsDocx(
            templatePageSizes = listOf(a4),
            fontName = null,
            certificates = listOf(
                listOf(
                    addition("links", 0.4).copy(textAlign = TextAlign.LEFT),
                    addition("mitte", 0.5).copy(textAlign = TextAlign.CENTER),
                    addition("rechts", 0.6).copy(textAlign = TextAlign.RIGHT),
                )
            ),
        )

        val framed = document.paragraphs.filter { it.ctp.pPr?.framePr != null }
        assertEquals(
            listOf(ParagraphAlignment.LEFT, ParagraphAlignment.CENTER, ParagraphAlignment.RIGHT),
            framed.map { it.alignment },
        )
        document.close()
    }

    @Test
    fun certificatesAreSeparatedByPageBreaks() {
        val document = doc(
            listOf(
                listOf(addition("1. Platz", 0.45)),
                listOf(addition("2. Platz", 0.45)),
                listOf(addition("3. Platz", 0.45)),
            )
        )

        val breaks = document.paragraphs.sumOf { paragraph ->
            paragraph.runs.sumOf { run -> run.ctr.brList.count { it.type?.toString() == "page" } }
        }
        // Zwei Umbrüche für drei Urkunden.
        assertEquals(2, breaks)
        document.close()
    }

    @Test
    fun multipleLinesBecomeStackedFramedParagraphs() {
        val document = doc(listOf(listOf(addition("Carina Hein\nMalte Hein", 0.45))))

        val framed = document.paragraphs.filter { it.ctp.pPr?.framePr != null }
        assertEquals(listOf("Carina Hein", "Malte Hein"), framed.map { it.text })

        // Die zweite Zeile sitzt genau eine Zeilenhöhe (24 pt = 480 Twips) unter der ersten.
        val ys = framed.map { it.ctp.pPr.framePr.y.toString().toLong() }
        assertEquals(480L, ys[1] - ys[0])
        document.close()
    }

    @Test
    fun documentCanBeWrittenAndReadBack() {
        val bytes = doc(listOf(listOf(addition("1. Platz", 0.45)))).toByteArray()

        java.io.File("testOutputs").mkdirs()
        java.io.File("testOutputs/urkunden.docx").writeBytes(bytes)

        val reopened = XWPFDocument(bytes.inputStream())
        assertTrue(reopened.paragraphs.any { it.text == "1. Platz" })
        reopened.close()
    }

    // --- Mehrseitige Vorlage (Teilnahmeurkunde) -----------------------------------------------

    /** Zwei Vorlagenseiten unterschiedlicher Größe - A4 und A5, damit ein Größenwechsel sichtbar wird. */
    private val a5 = DocxPageSize(PDRectangle.A5.width, PDRectangle.A5.height)

    @Test
    fun twoPageTemplateProducesTwoWordSectionsWithBothPlaceholders() {
        val document = gapDocumentsDocx(
            templatePageSizes = listOf(a4, a5),
            fontName = null,
            certificates = listOf(
                listOf(
                    addition("Seite eins", 0.45, page = 1),
                    addition("Seite zwei", 0.45, page = 2),
                )
            ),
        )

        val framed = document.paragraphs.filter { it.ctp.pPr?.framePr != null }
        assertEquals(listOf("Seite eins", "Seite zwei"), framed.map { it.text })

        // Der Platzhalter der ersten Vorlagenseite ist A4-breit gerahmt, der der zweiten A5-breit.
        val widthsTwips = framed.map { it.ctp.pPr.framePr.w.toString().toLong() }
        assertEquals(twips(a4.widthPoints), widthsTwips[0])
        assertEquals(twips(a5.widthPoints), widthsTwips[1])

        // Zwei unterschiedliche Seitengrößen sind in Word nur über zwei Abschnitte möglich: der
        // Body trägt die Größe des letzten Abschnitts (A5), ein Absatz dazwischen die des ersten (A4).
        val bodySectPr = document.document.body.sectPr
        assertEquals(twips(a5.widthPoints), bodySectPr.pgSz.w.toString().toLong())
        assertEquals(twips(a5.heightPoints), bodySectPr.pgSz.h.toString().toLong())

        val embeddedSectPrs = document.paragraphs.mapNotNull { it.ctp.pPr?.sectPr }
        assertEquals(1, embeddedSectPrs.size, "genau ein Abschnittswechsel für zwei Vorlagenseiten")
        assertEquals(twips(a4.widthPoints), embeddedSectPrs.first().pgSz.w.toString().toLong())
        assertEquals(twips(a4.heightPoints), embeddedSectPrs.first().pgSz.h.toString().toLong())

        document.close()
    }

    /**
     * Finding 3: Der Absatz, der die sectPr des endenden Abschnitts trägt, gehört fachlich noch
     * zur vorherigen (größenwechselnden) Seite. Eine Seite ohne eigene Platzhalter darf deshalb
     * nicht spurlos verschwinden - sie braucht trotzdem einen eigenen Anker-Absatz, sonst bleibt
     * ihr Abschnitt leer und Word/LibreOffice emittiert für ihn sehr wahrscheinlich kein Blatt.
     */
    @Test
    fun secondPageWithoutPlaceholdersStillGetsItsOwnParagraphAfterASizeChange() {
        val document = gapDocumentsDocx(
            templatePageSizes = listOf(a4, a5),
            fontName = null,
            certificates = listOf(
                listOf(addition("Seite eins", 0.45, page = 1)),
                // Seite 2 (A5) bekommt bewusst keinen Platzhalter.
            ),
        )

        val paragraphs = document.paragraphs
        val sectPrParagraphIndex = paragraphs.indexOfFirst { it.ctp.pPr?.sectPr != null }
        assertTrue(sectPrParagraphIndex >= 0, "Abschnittswechsel zwischen den unterschiedlich großen Seiten muss existieren")
        assertTrue(
            sectPrParagraphIndex < paragraphs.size - 1,
            "die platzhalterlose zweite Seite muss einen eigenen Absatz nach dem Abschnittswechsel haben, sonst bleibt ihr Abschnitt leer",
        )

        // Der Abschnittswechsel-Absatz trägt weiterhin die Größe der ersten Seite (A4) ...
        val embeddedSectPr = paragraphs[sectPrParagraphIndex].ctp.pPr.sectPr
        assertEquals(twips(a4.widthPoints), embeddedSectPr.pgSz.w.toString().toLong())
        assertEquals(twips(a4.heightPoints), embeddedSectPr.pgSz.h.toString().toLong())

        // ... und der letzte (Body-)Abschnitt bleibt in der Größe der zweiten Seite (A5), auch
        // wenn diese keine eigenen Platzhalter hat.
        val bodySectPr = document.document.body.sectPr
        assertEquals(twips(a5.widthPoints), bodySectPr.pgSz.w.toString().toLong())
        assertEquals(twips(a5.heightPoints), bodySectPr.pgSz.h.toString().toLong())

        document.close()
    }

    @Test
    fun placeholderNamingAPageTheTemplateDoesNotHaveIsDropped() {
        val document = gapDocumentsDocx(
            templatePageSizes = listOf(a4),
            fontName = null,
            certificates = listOf(
                listOf(
                    addition("bleibt", 0.45, page = 1),
                    addition("verschwindet", 0.45, page = 2),
                )
            ),
        )

        val framed = document.paragraphs.filter { it.ctp.pPr?.framePr != null }
        assertEquals(listOf("bleibt"), framed.map { it.text })
        document.close()
    }

    @Test
    fun twoPageDocumentCanBeWrittenAndReadBack() {
        // Für die visuelle Kontrolle (soffice --headless --convert-to pdf, dann pdftoppm): zwei
        // deutlich unterscheidbare Platzhalter auf zwei unterschiedlich großen Vorlagenseiten.
        val bytes = gapDocumentsDocx(
            templatePageSizes = listOf(a4, a5),
            fontName = null,
            certificates = listOf(
                listOf(
                    addition("SEITE EINS (A4)", 0.45, page = 1),
                    addition("SEITE ZWEI (A5)", 0.45, page = 2),
                )
            ),
        ).toByteArray()

        java.io.File("testOutputs").mkdirs()
        java.io.File("testOutputs/urkunden_zweiseitig.docx").writeBytes(bytes)

        val reopened = XWPFDocument(bytes.inputStream())
        assertEquals(
            listOf("SEITE EINS (A4)", "SEITE ZWEI (A5)"),
            reopened.paragraphs.filter { it.ctp.pPr?.framePr != null }.map { it.text },
        )
        reopened.close()
    }

    @Test
    fun singlePageAwardCertificatePathStaysUnchangedForMultipleCertificates() {
        // Die Siegerurkunde hat laut Fachlogik immer genau eine Vorlagenseite - mehrere Urkunden
        // in einem Dokument bleiben deshalb einfache Seitenumbrüche im selben Abschnitt, nicht
        // mehrere Word-Abschnitte.
        val document = doc(
            listOf(
                listOf(addition("1. Platz", 0.45)),
                listOf(addition("2. Platz", 0.45)),
            )
        )

        val embeddedSectPrs = document.paragraphs.mapNotNull { it.ctp.pPr?.sectPr }
        assertTrue(embeddedSectPrs.isEmpty(), "keine Abschnittswechsel bei gleicher Seitengröße")
        document.close()
    }

    private fun twips(points: Float): Long = (points * 20f).roundToLong()
}
