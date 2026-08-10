package de.lambda9.ready2race.backend.pdf

import de.lambda9.ready2race.backend.text.TextAlign

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
 * Braucht der Text mehr Zeilen, als in den Kasten passen (seit dem Umbruch der Vereinskette der
 * Normalfall, siehe [de.lambda9.ready2race.backend.pdf.GapTextWrap]), wird [blockTop] negativ und
 * der Textblock wächst **symmetrisch** über den Kasten hinaus - gleich viel nach oben wie nach
 * unten, die Mitte bleibt, wo die Vorlage sie vorgesehen hat. Zahlen für eine dreizeilige Kette
 * bei 18 pt in einem Kasten von 4 % Seitenhöhe: 64,8 pt Blockhöhe gegen 33,7 pt Kastenhöhe, also
 * gut 15 pt Überstand nach jeder Seite. Wer einen Platzhalter enger als das an einen anderen
 * setzt, bekommt sie übereinander - das entscheidet die Vorlage, nicht der Renderer.
 */
data class GapTextMetrics(
    val fontSize: Float,
    val lineHeight: Float,
    val blockTop: Float,
)

/**
 * Die tatsächlich verwendete Schriftgröße - die einzige Stelle, an der die Regel "ohne gesetzte
 * Größe gilt die Kastenhöhe" steht. Auch der Umbruch ([de.lambda9.ready2race.backend.pdf.wrappedToBoxes])
 * braucht sie, denn er muss in derselben Größe messen, in der später gesetzt wird.
 */
fun AdditionalText.gapFontSize(boxHeight: Float): Float = fontSize ?: boxHeight

/**
 * @param boxHeight Höhe des Platzhalterkastens in derselben Einheit wie [AdditionalText.fontSize] (pt).
 * @param lineCount Anzahl der (bereits umgebrochenen) Zeilen des Textinhalts.
 */
fun AdditionalText.gapTextMetrics(boxHeight: Float, lineCount: Int): GapTextMetrics {
    val fontSize = gapFontSize(boxHeight)
    val lineHeight = fontSize * 1.2f
    val blockTop = (boxHeight - lineHeight * lineCount) / 2
    return GapTextMetrics(fontSize = fontSize, lineHeight = lineHeight, blockTop = blockTop)
}
