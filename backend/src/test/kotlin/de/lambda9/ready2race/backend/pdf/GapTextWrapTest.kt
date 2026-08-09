package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Der Umbruch der Vereinskette. Zwei Messarten stehen hier nebeneinander, mit Absicht:
 *
 * - eine erfundene Schrift mit fester Zeichenbreite, damit die erwarteten Zeilen im Test ablesbar
 *   sind statt aus Schriftmaßen erraten;
 * - echte Helvetica-Maße für die eine Zusicherung, auf die es am Ende ankommt: keine Zeile ist
 *   breiter als der Kasten. Genau diese Messung macht auch der Renderer.
 */
class GapTextWrapTest {

    /** Eine Schrift, in der jedes Zeichen gleich breit ist - eine Zeichenzahl als Breite. */
    private fun monospace(charsPerLine: Int): Pair<Float, (String) -> Float> =
        charsPerLine.toFloat() to { text: String -> text.length.toFloat() }

    private val mainz = "Mainzer Ruder-Verein 1878 e.V."
    private val marburg = "Marburger Ruderverein von 1911 e.V."
    private val flensburg = "Ruderklub Flensburg e.V."
    private val nuertingen = "Ruderclub Nürtingen"
    private val kiel = "Erster Kieler Ruder-Club von 1862 e.V."

    private val fiveClubs = listOf(mainz, marburg, flensburg, nuertingen, kiel)
        .joinToString(GapTextWrap.CHAIN_SEPARATOR)

    /** Der Name aus den echten Meldedaten, der allein keine Urkundenbreite füllt. */
    private val leuphana = "Ruder-Club Allemannia von 1866 e.V. - Leuphana Universität Lüneburg"

    /** Ein einzelner Verein, der passt, bleibt eine Zeile - für die reinen Vereinsboote. */
    @Test
    fun aChainThatFitsStaysOnOneLine() {
        val (maxWidth, measure) = monospace(80)

        assertEquals(listOf(mainz), GapTextWrap.lines(mainz, maxWidth, measure))
    }

    /**
     * Der Fall, um den es geht: umgebrochen wird zwischen zwei Vereinen, nie mitten in einem
     * Namen. Jede Zeile nimmt so viele vollständige Vereine, wie hineingehen.
     */
    @Test
    fun aChainBreaksBetweenClubsNotInsideAClubName() {
        val (maxWidth, measure) = monospace(70)

        val lines = GapTextWrap.lines(fiveClubs, maxWidth, measure)

        assertEquals(
            listOf(
                "$mainz${GapTextWrap.CHAIN_SEPARATOR}$marburg",
                "$flensburg${GapTextWrap.CHAIN_SEPARATOR}$nuertingen",
                kiel,
            ),
            lines,
        )
        // Kein Glied ist zerrissen: jede Zeile besteht aus vollständigen Vereinsnamen.
        val rejoined = lines.flatMap { it.split(GapTextWrap.CHAIN_SEPARATOR) }
        assertEquals(fiveClubs.split(GapTextWrap.CHAIN_SEPARATOR), rejoined)
    }

    /** Ein schmaler Kasten ergibt einen Verein je Zeile - und trotzdem keinen zerrissenen Namen. */
    @Test
    fun aNarrowBoxPutsEveryClubOnItsOwnLine() {
        val (maxWidth, measure) = monospace(40)

        assertEquals(fiveClubs.split(GapTextWrap.CHAIN_SEPARATOR), GapTextWrap.lines(fiveClubs, maxWidth, measure))
    }

    /**
     * Die Rückfallebene: passt ein einzelner Vereinsname nicht in die Breite, wird für ihn an
     * Wortgrenzen weitergebrochen. Lieber ein umgebrochener langer Name als einer über dem Rand.
     */
    @Test
    fun aSingleClubNameTooWideForTheBoxBreaksAtWords() {
        val (maxWidth, measure) = monospace(30)

        val lines = GapTextWrap.lines(leuphana, maxWidth, measure)

        assertTrue(lines.size > 1, "hätte umbrechen müssen: $lines")
        lines.forEach { assertTrue(measure(it) <= maxWidth, "zu breite Zeile: '$it'") }
        // Zusammengesetzt steht wieder der ursprüngliche Name da - kein Zeichen ist verloren.
        assertEquals(leuphana, lines.joinToString(" "))
    }

    /** Derselbe Name als Glied einer Kette: der Rest der Kette läuft danach normal weiter. */
    @Test
    fun anOverlongClubInsideAChainDoesNotDerailTheRest() {
        val (maxWidth, measure) = monospace(30)

        val lines = GapTextWrap.lines(
            "$leuphana${GapTextWrap.CHAIN_SEPARATOR}$nuertingen",
            maxWidth,
            measure,
        )

        lines.forEach { assertTrue(measure(it) <= maxWidth, "zu breite Zeile: '$it'") }
        assertTrue(lines.last().endsWith(nuertingen), "letzte Zeile: '${lines.last()}'")
    }

    /**
     * Ein einzelnes zu breites Wort bleibt stehen: es zu zerhacken machte den Namen unleserlich,
     * ohne ihn unterzubringen. Der Umbruch darf daran nicht in eine Endlosschleife oder in eine
     * Zeile je Buchstabe laufen.
     */
    @Test
    fun aSingleWordWiderThanTheBoxSurvivesUntouched() {
        val (maxWidth, measure) = monospace(10)

        assertEquals(listOf("Donaudampfschifffahrtsgesellschaft"), GapTextWrap.lines("Donaudampfschifffahrtsgesellschaft", maxWidth, measure))
    }

    /** Vorhandene Zeilenumbrüche (die Namensliste einer Mannschaftsurkunde) bleiben erhalten. */
    @Test
    fun explicitLineBreaksSurvive() {
        val (maxWidth, measure) = monospace(80)

        assertEquals(
            listOf("Carina Hein", "Malte Hein"),
            GapTextWrap.lines("Carina Hein\nMalte Hein", maxWidth, measure),
        )
    }

    /** Ein Platzhalter ohne Breite darf den Text nicht senkrecht stellen. */
    @Test
    fun aBoxWithoutWidthLeavesTheTextAlone() {
        assertEquals(listOf(fiveClubs), GapTextWrap.lines(fiveClubs, 0f, { it.length.toFloat() }))
    }

    /**
     * Die Zusicherung, auf die es ankommt - mit denselben Schriftmaßen, mit denen der Renderer
     * setzt: eine fünfgliedrige Kette landet auf mehreren Zeilen und **keine** davon ist breiter
     * als der Kasten. Ohne den Umbruch war diese Kette bei 18 pt 2,19-mal so breit wie A4.
     */
    @Test
    fun withRealFontMetricsNoLineIsWiderThanTheBox() {
        val font: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)

        for (fontSize in listOf(14f, 18f, 24f)) {
            // Der Kasten einer Urkunde: volle Seitenbreite abzüglich eines schmalen Rands.
            val boxWidth = PDRectangle.A4.width * 0.9f
            val measure = { text: String -> font.getStringWidth(text) / 1000 * fontSize }

            val lines = GapTextWrap.lines(fiveClubs, boxWidth, measure)

            assertTrue(lines.size > 1, "bei $fontSize pt hätte umgebrochen werden müssen: $lines")
            lines.forEach {
                assertTrue(
                    measure(it) <= boxWidth,
                    "bei $fontSize pt ist '$it' ${measure(it)} pt breit, der Kasten nur $boxWidth pt",
                )
            }
        }
    }

    /** Dasselbe für die Rückfallebene, ebenfalls mit echten Maßen. */
    @Test
    fun withRealFontMetricsTheOverlongClubNameFitsToo() {
        val font: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val fontSize = 24f
        val boxWidth = PDRectangle.A4.width * 0.5f
        val measure = { text: String -> font.getStringWidth(text) / 1000 * fontSize }

        val lines = GapTextWrap.lines(leuphana, boxWidth, measure)

        assertTrue(lines.size > 1, "hätte umbrechen müssen: $lines")
        lines.forEach {
            assertTrue(measure(it) <= boxWidth, "'$it' ist ${measure(it)} pt breit, der Kasten nur $boxWidth pt")
        }
    }
}
