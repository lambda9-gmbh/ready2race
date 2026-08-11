package de.lambda9.ready2race.backend.pdf

import de.lambda9.ready2race.backend.text.TextAlign
import kotlin.math.max

data class AdditionalText(
    val content: String,
    val page: Int,
    val relLeft: Double,
    val relTop: Double,
    val relWidth: Double,
    val relHeight: Double,
    val textAlign: TextAlign,
    val fontSize: Float? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
)

/**
 * Geometrie, die für einen Platzhalter in beiden Formaten (PDF und DOCX) identisch sein muss: die
 * tatsächlich verwendete Schriftgröße, die daraus abgeleitete Zeilenhöhe, und die senkrechte Position
 * des Textblocks innerhalb des Platzhalterkastens. [blockTop] ist die einzige Definition der
 * senkrechten Zentrierung, auf die sich beide Renderer stützen, damit sie exakt denselben Punkt auf
 * dem vorgedruckten Papier treffen - vorher stand dieselbe Formel zweimal in zwei Koordinatensystemen
 * im PDF- und im DOCX-Renderer, sodass eine Änderung an einer Stelle die Formate auseinanderlaufen
 * lassen konnte, ohne dass eine der beiden Testsuiten das bemerkt hätte.
 *
 * [blockTop] ist top-down gemessen, als Versatz von der Kastenoberkante nach unten:
 * - Der DOCX-Renderer rechnet ohnehin top-down und verwendet [blockTop] direkt: die absolute Position
 *   auf der Seite ist `Kastenoberkante + blockTop`.
 * - Der PDF-Renderer rechnet bottom-up und muss deshalb einmal umrechnen: die Kastenoberkante in
 *   PDF-Koordinaten ist `y + Kastenhöhe` (y = Unterkante des Kastens), die Blockoberkante also
 *   `(y + Kastenhöhe) - blockTop`.
 *
 * Ist [AdditionalText.fontSize] nicht gesetzt, gilt die Kastenhöhe als Schriftgröße - das ist die
 * einzige Stelle, an der diese Regel definiert ist.
 *
 * Braucht ein **mehrzeiliger** Text mehr Höhe, als der Kasten hat (seit dem Umbruch der
 * Vereinskette und den Namenslisten großer Boote der Normalfall, siehe
 * [de.lambda9.ready2race.backend.pdf.GapTextWrap]), wird die Schrift so weit verkleinert, dass der
 * Block gerade in den Kasten passt - [fontSize] kann also kleiner sein als
 * [AdditionalText.fontSize], siehe [gapTextMetrics]. Bis zum 11.08.2026 wurde stattdessen
 * [blockTop] negativ und der Block wuchs symmetrisch über den Kasten hinaus; bei fünf Namen
 * (Doppelvierer mit Steuerleuten) überdruckte der Namensblock damit die fest positionierte
 * Wettkampfzeile darüber und die Vereinszeile darunter.
 *
 * Nur wenn selbst die Untergrenze [GAP_TEXT_MIN_FONT_SIZE] nicht reicht, gilt weiter das alte
 * Verhalten: [blockTop] wird negativ und der Block wächst symmetrisch über den Kasten hinaus -
 * gleich viel nach oben wie nach unten, die Mitte bleibt, wo die Vorlage sie vorgesehen hat.
 */
data class GapTextMetrics(
    val fontSize: Float,
    val lineHeight: Float,
    val blockTop: Float,
)

/** Zeilenhöhe je Punkt Schriftgröße - der eine Faktor, mit dem beide Renderer rechnen. */
const val GAP_TEXT_LINE_HEIGHT_FACTOR = 1.2f

/**
 * Untergrenze für das Schrumpfen in [gapTextMetrics]. Unter 6 pt ist ein Name auf einer Urkunde
 * praktisch nicht mehr lesbar - dann lieber der sofort sichtbare, symmetrische Überlauf über den
 * Kasten hinaus als Text, den niemand mehr entziffern kann. 6 pt ist die übliche kleinste
 * Druckgröße (Fußnoten/Kleingedrucktes) und erlaubt im praktisch engsten Fall - fünf Namen in
 * einem einzeiligen Kasten - noch das vollständige Schrumpfen, solange der Kasten mindestens
 * 36 pt hoch ist.
 */
const val GAP_TEXT_MIN_FONT_SIZE = 6f

/**
 * Die tatsächlich verwendete Schriftgröße - die einzige Stelle, an der die Regel "ohne gesetzte
 * Größe gilt die Kastenhöhe" steht. Auch der Umbruch ([de.lambda9.ready2race.backend.pdf.wrappedToBoxes])
 * braucht sie, denn er muss in derselben Größe messen, in der später gesetzt wird.
 */
fun AdditionalText.gapFontSize(boxHeight: Float): Float = fontSize ?: boxHeight

/**
 * Passt ein mehrzeiliger Block nicht in seinen Kasten (`lineHeight * lineCount > boxHeight`), wird
 * die Schrift auf `boxHeight / (1,2 * lineCount)` verkleinert - genau die Größe, bei der der Block
 * den Kasten exakt füllt - aber nie unter [GAP_TEXT_MIN_FONT_SIZE]. [GapTextMetrics.blockTop] ist
 * damit im Normalfall nie mehr negativ; nur an der Untergrenze bleibt der alte, symmetrische
 * Überlauf.
 *
 * Geschrumpft wird bewusst erst ab zwei Zeilen: eine einzelne Zeile überragt ihren Kasten schon
 * immer um die 20 % Durchschuss (Standardgröße = Kastenhöhe, Zeilenhöhe = das 1,2-fache), und jede
 * bestehende Vorlage ist darauf eingemessen - einzeilige Platzhalter zu schrumpfen hieße, jede
 * heute korrekte Urkunde zu verändern.
 *
 * Der Zeilenumbruch misst mit der Ausgangsgröße; nach einem Schrumpfen bricht [wrappedToBoxes]
 * deshalb einmal mit der kleineren Größe neu um und schreibt sie in den Platzhalter zurück. Der
 * anschließende Aufruf dieser Funktion in den Renderern ist damit stabil: mit der geschrumpften
 * Größe als Basis und höchstens gleich vielen Zeilen greift die Bedingung nicht erneut, bzw. die
 * Untergrenze liefert denselben Wert.
 *
 * @param boxHeight Höhe des Platzhalterkastens in derselben Einheit wie [AdditionalText.fontSize] (pt).
 * @param lineCount Anzahl der (bereits umgebrochenen) Zeilen des Textinhalts.
 */
fun AdditionalText.gapTextMetrics(boxHeight: Float, lineCount: Int): GapTextMetrics {
    val baseSize = gapFontSize(boxHeight)
    val fontSize =
        if (lineCount > 1 && baseSize * GAP_TEXT_LINE_HEIGHT_FACTOR * lineCount > boxHeight) {
            max(GAP_TEXT_MIN_FONT_SIZE, boxHeight / (GAP_TEXT_LINE_HEIGHT_FACTOR * lineCount))
        } else {
            baseSize
        }
    val lineHeight = fontSize * GAP_TEXT_LINE_HEIGHT_FACTOR
    val blockTop = (boxHeight - lineHeight * lineCount) / 2
    return GapTextMetrics(fontSize = fontSize, lineHeight = lineHeight, blockTop = blockTop)
}
