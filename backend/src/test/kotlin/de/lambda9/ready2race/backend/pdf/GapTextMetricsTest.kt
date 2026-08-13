package de.lambda9.ready2race.backend.pdf

import de.lambda9.ready2race.backend.text.TextAlign
import org.apache.pdfbox.pdmodel.common.PDRectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Die Schrumpf-Logik in [gapTextMetrics] (seit dem 11.08.2026, auf Wunsch des Nutzers nach einer
 * echten Urkunde, auf der der fünfzeilige Namensblock eines Doppelvierers mit Steuerfrau die
 * Wettkampf- und die Vereinszeile überdruckte): passt ein mehrzeiliger Block nicht in seinen
 * Kasten, wird die Schrift verkleinert statt dass der Block symmetrisch über den Kasten
 * hinauswächst. Einzeilige Platzhalter und passende Blöcke behalten exakt die alte Metrik -
 * jede bestehende Vorlage ist darauf eingemessen.
 */
class GapTextMetricsTest {

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

    @Test
    fun singleLineWithoutConfiguredSizeKeepsTheOldMetricsExactly() {
        // Regressionsschutz: die Standardgröße (= Kastenhöhe) überragt ihren Kasten schon immer
        // um die 20 % Durchschuss (blockTop leicht negativ). Das bleibt bewusst so - schrumpfte
        // auch die einzelne Zeile, verschöbe sich jede heute korrekte Urkunde.
        val metrics = addition("1. Platz", fontSize = null).gapTextMetrics(boxHeight = 40f, lineCount = 1)

        assertEquals(40f, metrics.fontSize)
        assertEquals(48f, metrics.lineHeight)
        assertEquals(-4f, metrics.blockTop)
    }

    @Test
    fun singleLineWithConfiguredSizeKeepsTheOldMetricsExactly() {
        val metrics = addition("1. Platz", fontSize = 20f).gapTextMetrics(boxHeight = 40f, lineCount = 1)

        assertEquals(20f, metrics.fontSize)
        assertEquals(24f, metrics.lineHeight)
        assertEquals(8f, metrics.blockTop)
    }

    @Test
    fun multiLineBlockThatFitsKeepsTheOldMetricsExactly() {
        val metrics = addition("Carina Hein\nMalte Hein", fontSize = 10f).gapTextMetrics(boxHeight = 40f, lineCount = 2)

        assertEquals(10f, metrics.fontSize)
        assertEquals(12f, metrics.lineHeight)
        assertEquals(8f, metrics.blockTop)
    }

    @Test
    fun fiveLinesInAOneLineBoxShrinkUntilTheBlockFillsTheBoxExactly() {
        // Der Bugfall: fünf Namen in einem Kasten, der auf eine Zeile eingemessen ist. Statt des
        // symmetrischen Überwachsens (vorher: blockTop = (40 - 240) / 2 = -100) schrumpft die
        // Schrift auf boxHeight / (1,2 * 5) und der Block füllt den Kasten exakt.
        val metrics = addition("A\nB\nC\nD\nE", fontSize = null).gapTextMetrics(boxHeight = 40f, lineCount = 5)

        assertEquals(40f / 6f, metrics.fontSize, "geschrumpfte Schriftgröße")
        assertTrue(metrics.blockTop >= -0.001f, "blockTop darf nach dem Schrumpfen nicht negativ sein")
        assertTrue(
            metrics.blockTop + metrics.lineHeight * 5 <= 40f + 0.001f,
            "der geschrumpfte Block muss im Kasten bleiben",
        )
    }

    @Test
    fun shrinkingStopsAtTheMinimumFontSize() {
        // Fünf Zeilen in einem 20-pt-Kasten bräuchten 3,3 pt - darunter ist eine Urkunde
        // unleserlich. Die Untergrenze greift, und der Block läuft wieder symmetrisch über
        // (blockTop negativ): der sichtbare Überlauf ist das kleinere Übel gegenüber Text,
        // den niemand mehr entziffern kann.
        val metrics = addition("A\nB\nC\nD\nE", fontSize = null).gapTextMetrics(boxHeight = 20f, lineCount = 5)

        assertEquals(GAP_TEXT_MIN_FONT_SIZE, metrics.fontSize)
        assertEquals(GAP_TEXT_MIN_FONT_SIZE * 1.2f, metrics.lineHeight)
        assertEquals((20f - metrics.lineHeight * 5) / 2, metrics.blockTop)
        assertTrue(metrics.blockTop < 0f, "an der Untergrenze bleibt der symmetrische Überlauf")
    }

    @Test
    fun shrinkingRewrapsWithTheSmallerFontSizeAndStaysStable() {
        // Der Umbruch misst zunächst mit der Ausgangsgröße; nach dem Schrumpfen passt in der
        // kleineren Schrift mehr in eine Zeile. wrappedToBoxes bricht deshalb einmal neu um und
        // schreibt die geschrumpfte Größe in den Platzhalter zurück - der Metrik-Aufruf der
        // Renderer (mit dieser Größe als Basis) schrumpft dann nicht weiter.
        val pageWidth = PDRectangle.A4.width
        val pageHeight = PDRectangle.A4.height
        val original = addition(
            content = (1..6).joinToString(GapTextWrap.CHAIN_SEPARATOR) { "Verein $it" },
            fontSize = 30f,
        ).copy(relWidth = 0.3, relHeight = 0.15)

        val boxWidth = pageWidth * 0.3f
        val boxHeight = pageHeight * 0.15f

        GapTextWidths.of(null).use { widths ->
            val naiveLineCount = GapTextWrap
                .lines(original.content, boxWidth) { widths.width(it, 30f, false, false) }
                .size
            val wrapped = listOf(original).wrappedToBoxes(pageWidth, pageHeight, widths).single()
            val lines = wrapped.content.split("\n")

            assertNotNull(wrapped.fontSize, "die geschrumpfte Größe muss im Platzhalter stehen")
            assertTrue(wrapped.fontSize!! < 30f, "die Schrift muss geschrumpft sein")
            assertTrue(
                lines.size < naiveLineCount,
                "in der kleineren Schrift (${wrapped.fontSize}) müssen Zeilen zusammenrücken " +
                    "(${lines.size} statt $naiveLineCount)",
            )
            lines.forEach { line ->
                assertTrue(
                    widths.width(line, wrapped.fontSize!!, false, false) <= boxWidth,
                    "Zeile '$line' muss in der geschrumpften Größe in die Kastenbreite passen",
                )
            }

            // Stabilität: genau der Aufruf, den beide Renderer nach dem Umbruch machen.
            val metrics = wrapped.gapTextMetrics(boxHeight, lines.size)
            assertEquals(wrapped.fontSize!!, metrics.fontSize, "die Metrik darf nicht weiter schrumpfen")
            assertTrue(metrics.blockTop >= 0f, "der neu umgebrochene Block muss im Kasten liegen")
        }
    }
}
