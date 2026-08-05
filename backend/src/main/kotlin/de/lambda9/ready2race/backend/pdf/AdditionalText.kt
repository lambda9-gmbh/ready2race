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
 */
data class GapTextMetrics(
    val fontSize: Float,
    val lineHeight: Float,
    val blockTop: Float,
)

/**
 * @param boxHeight Höhe des Platzhalterkastens in derselben Einheit wie [AdditionalText.fontSize] (pt).
 * @param lineCount Anzahl der (bereits umgebrochenen) Zeilen des Textinhalts.
 */
fun AdditionalText.gapTextMetrics(boxHeight: Float, lineCount: Int): GapTextMetrics {
    val fontSize = this.fontSize ?: boxHeight
    val lineHeight = fontSize * 1.2f
    val blockTop = (boxHeight - lineHeight * lineCount) / 2
    return GapTextMetrics(fontSize = fontSize, lineHeight = lineHeight, blockTop = blockTop)
}
