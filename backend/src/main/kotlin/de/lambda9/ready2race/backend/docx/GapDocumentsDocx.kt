package de.lambda9.ready2race.backend.docx

import de.lambda9.ready2race.backend.pdf.AdditionalText
import de.lambda9.ready2race.backend.pdf.gapTextMetrics
import de.lambda9.ready2race.backend.text.TextAlign
import de.lambda9.ready2race.backend.text.sanitizeNonPrintable
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHAnchor
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHeightRule
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STVAnchor
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STWrap
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Word rechnet in Twips: 1 Punkt = 20 Twips. */
private const val TWIPS_PER_POINT = 20f

private fun twips(points: Float): BigInteger =
    BigInteger.valueOf(points.times(TWIPS_PER_POINT).roundToLong())

/** Seitengröße einer Vorlagenseite in Punkt - genau das, was `PDPage.mediaBox` liefert. */
data class DocxPageSize(val widthPoints: Float, val heightPoints: Float)

/** Eine zu rendernde Word-Seite: ihre Größe (von der Vorlagenseite) und ihre Platzhalter. */
private data class RenderPage(val size: DocxPageSize, val additions: List<AdditionalText>)

/**
 * Erzeugt eine Urkundenserie als Word-Dokument.
 *
 * [templatePageSizes] beschreibt die Seiten der PDF-Vorlage in Vorlagenreihenfolge (Index 0 =
 * Seite 1, Index 1 = Seite 2, ...). Jede Urkunde in [certificates] bekommt genau so viele
 * Word-Seiten, wie die Vorlage Seiten hat - jede in der Größe ihrer Vorlagenseite, mit den
 * Platzhaltern, deren `page`-Feld auf diese Seite zeigt. Ein Platzhalter, dessen Seite die Vorlage
 * nicht hat, wird fallengelassen - wie im PDF-Renderer für die Teilnahmeurkunde ([de.lambda9.ready2race.backend.pdf.document]).
 *
 * Für die Siegerurkunde hat die Vorlage laut Fachlogik immer genau eine Seite - [templatePageSizes]
 * hat dann genau ein Element, und jede Urkunde wird wie bisher zu genau einer Word-Seite im selben
 * Abschnitt. Für die Teilnahmeurkunde darf die Vorlage mehrseitig sein: unterschiedlich große
 * Vorlagenseiten lassen sich in Word nur über einen Abschnittswechsel abbilden (Word kennt Seitengröße
 * nur pro Abschnitt), einen einfachen Seitenumbruch gibt es deshalb nur zwischen zwei Seiten
 * gleicher Größe.
 *
 * Ein Hintergrundbild wird bewusst nicht gesetzt — gedruckt wird auf vorgedrucktes Papier.
 */
fun gapDocumentsDocx(
    templatePageSizes: List<DocxPageSize>,
    fontName: String?,
    certificates: List<List<AdditionalText>>,
): XWPFDocument {
    val document = XWPFDocument()

    val renderPages = certificates.flatMap { additions ->
        templatePageSizes.mapIndexed { index, size ->
            RenderPage(size = size, additions = additions.filter { it.page == index + 1 })
        }
    }

    renderPages.forEachIndexed { pageIndex, renderPage ->
        val previousSize = renderPages.getOrNull(pageIndex - 1)?.size
        val simplePageBreak = pageIndex > 0 && previousSize == renderPage.size

        if (pageIndex > 0 && previousSize != renderPage.size) {
            // Andere Seitengröße als die vorherige: Word kennt unterschiedliche Seitengrößen nur
            // über einen Abschnittswechsel. Der endende Abschnitt bekommt seine sectPr (inkl.
            // Seitengröße) auf einem eigenen, dedizierten Absatz - das ist in OOXML die einzige
            // Stelle, an der ein Abschnitt (außer dem letzten) seine Seitengröße definiert; der
            // letzte Abschnitt bekommt sie stattdessen auf Body-Ebene, siehe unten. Dieser Absatz
            // gehört noch zum endenden Abschnitt und ist NICHT der Anker der neuen Seite - der wird
            // unten unabhängig davon angelegt, sonst bliebe eine platzhalterlose Seite nach einem
            // Größenwechsel ganz ohne eigenen Absatz und ihr Abschnitt damit leer.
            val sectionEnd = document.createParagraph()
            val pPr = sectionEnd.ctp.pPr ?: sectionEnd.ctp.addNewPPr()
            applyPageSize(pPr.addNewSectPr(), previousSize!!)
        }

        // Anker für jede Seite, unabhängig von ihren Platzhaltern - eine Seite ohne Platzhalter
        // (z.B. eine reine Rückseite) muss trotzdem als eigener Absatz existieren, sonst emittiert
        // Word/LibreOffice für ihren Abschnitt sehr wahrscheinlich kein eigenes Blatt.
        val anchor = document.createParagraph()
        if (simplePageBreak) {
            // Gleiche Seitengröße wie die vorherige Seite: ein einfacher Seitenumbruch reicht,
            // beide Seiten bleiben im selben Word-Abschnitt (unverändertes Verhalten für die
            // Siegerurkunde, deren Vorlage immer nur eine Seitengröße kennt).
            anchor.createRun().addBreak(BreakType.PAGE)
        }

        renderPage.additions.forEach { addition ->
            appendAdditionParagraphs(document, renderPage.size, addition, fontName)
        }
    }

    // Die Seitengröße des letzten Abschnitts sitzt auf Body-Ebene statt an einem Absatz.
    val lastSectionSize = renderPages.lastOrNull()?.size ?: templatePageSizes.firstOrNull()
    lastSectionSize?.let { applyPageSize(document.document.body.addNewSectPr(), it) }

    return document
}

private fun applyPageSize(sectPr: CTSectPr, size: DocxPageSize) {
    val pgSz = sectPr.addNewPgSz()
    pgSz.w = twips(size.widthPoints)
    pgSz.h = twips(size.heightPoints)
    val pgMar = sectPr.addNewPgMar()
    pgMar.top = BigInteger.ZERO
    pgMar.bottom = BigInteger.ZERO
    pgMar.left = BigInteger.ZERO
    pgMar.right = BigInteger.ZERO
    pgMar.header = BigInteger.ZERO
    pgMar.footer = BigInteger.ZERO
    pgMar.gutter = BigInteger.ZERO
}

private fun appendAdditionParagraphs(
    document: XWPFDocument,
    pageSize: DocxPageSize,
    addition: AdditionalText,
    fontName: String?,
) {
    val lines = addition.content.split("\n").map { it.sanitizeNonPrintable() }

    // Wie im PDF wird der Textblock senkrecht im Platzhalterkasten zentriert, damit beide
    // Formate dieselbe Stelle auf dem Papier treffen. GapTextMetrics.blockTop ist top-down
    // definiert (Versatz von der Kastenoberkante) - DOCX rechnet ohnehin top-down und
    // verwendet ihn deshalb direkt, ohne weitere Umrechnung.
    val boxTop = pageSize.heightPoints * addition.relTop.toFloat()
    val boxHeight = pageSize.heightPoints * addition.relHeight.toFloat()
    val metrics = addition.gapTextMetrics(boxHeight, lines.size)
    val lineHeight = metrics.lineHeight
    val blockTop = boxTop + metrics.blockTop

    lines.forEachIndexed { lineIndex, line ->
        val paragraph = document.createParagraph()
        paragraph.alignment = when (addition.textAlign) {
            TextAlign.LEFT -> ParagraphAlignment.LEFT
            TextAlign.CENTER -> ParagraphAlignment.CENTER
            TextAlign.RIGHT -> ParagraphAlignment.RIGHT
        }

        // Jede Zeile erhält ihren eigenen Rahmen, eine Zeile hoch. Dadurch braucht Word
        // keinen Zeilenumbruch zu berechnen und die Zeilen sitzen exakt wie im PDF.
        applyFrame(
            paragraph = paragraph,
            xPoints = pageSize.widthPoints * addition.relLeft.toFloat(),
            yPoints = blockTop + lineHeight * lineIndex,
            widthPoints = pageSize.widthPoints * addition.relWidth.toFloat(),
            heightPoints = lineHeight,
        )

        val run = paragraph.createRun()
        run.setText(line)
        fontName?.let { run.fontFamily = it }
        // Wie im PDF-Renderer: ohne konfigurierte Größe wird bei der Kastenhöhe gerendert,
        // sonst würde der sichtbare Text von dem für ihn vorgesehenen Rahmen abweichen.
        run.fontSize = metrics.fontSize.roundToInt()
        run.isBold = addition.bold
        run.isItalic = addition.italic
    }
}

private fun applyFrame(
    paragraph: XWPFParagraph,
    xPoints: Float,
    yPoints: Float,
    widthPoints: Float,
    heightPoints: Float,
) {
    val pPr = paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()
    val frame = pPr.framePr ?: pPr.addNewFramePr()
    frame.x = twips(xPoints)
    frame.y = twips(yPoints)
    frame.w = twips(widthPoints)
    frame.h = twips(heightPoints)
    frame.hRule = STHeightRule.AT_LEAST
    frame.hAnchor = STHAnchor.PAGE
    frame.vAnchor = STVAnchor.PAGE
    frame.wrap = STWrap.NOT_BESIDE
}

fun XWPFDocument.toByteArray(): ByteArray {
    val out = ByteArrayOutputStream()
    write(out)
    val bytes = out.toByteArray()
    out.close()
    return bytes
}
