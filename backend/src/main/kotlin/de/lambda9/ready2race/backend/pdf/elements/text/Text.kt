package de.lambda9.ready2race.backend.pdf.elements.text

import de.lambda9.ready2race.backend.pdf.*
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts

data class Text(
    val newLine: Boolean,
    val content: String,
    val fontSize: Float,
    val lineHeight: Float,
    val fontStyle: FontStyle,
    override val padding: Padding,
) : Element {

    private val font = when (fontStyle) {
        FontStyle.NORMAL -> PDType1Font(Standard14Fonts.FontName.HELVETICA)
        FontStyle.BOLD -> PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        FontStyle.ITALIC -> PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)
        FontStyle.BOLD_ITALIC -> PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE)
    }

    private val width = font.getStringWidth(content) / 1000 * fontSize
    private val height = lineHeight * fontSize * font.fontDescriptor.capHeight / 1000

    private val yOffset = fontSize / 2.5f

    private var lines: List<String>? = null

    private fun computeLines(context: SizeContext): List<String> {

        if (lines != null) {
            return lines!!
        }

        val tmpLines: MutableList<String> = mutableListOf()

        val xMax = context.parentContentWidth

        var x = if (newLine) { 0f } else { context.startPosition.x }

        if (x + width <= xMax) {
            tmpLines.add(content)
        } else {
            val words = content.split("""\s""".toRegex()).toMutableList()
            while (words.isNotEmpty()) {
                val w = xMax - x
                val firstWordLength = fontSize * font.getStringWidth(words.first()) / 1000
                if (firstWordLength > w) {
                    if (x > 0f) {
                        tmpLines.add("")
                    } else {
                        val word = words.first()
                        var left = 0
                        var right = word.length
                        var lastFittingCandidate = word
                        var done = false
                        while (!done) {
                            val mid = left + (right - left) / 2
                            if (right <= mid || left >= mid) {
                                done = true
                            }
                            val candidate = word.take(mid)
                            val candidateWidth = font.getStringWidth(candidate) / 1000 * fontSize
                            if (candidateWidth > w) {
                                right = mid
                            } else {
                                lastFittingCandidate = candidate
                                left = mid
                            }
                        }
                        tmpLines.add(lastFittingCandidate)
                        val remaining = word.removePrefix(lastFittingCandidate)
                        words[0] = remaining
                    }
                } else {
                    var subLine: String? = null
                    do {
                        val lineCandidate =
                            listOfNotNull(subLine, words.first()).joinToString(" ")
                        val adding =
                            font.getStringWidth(lineCandidate) / 1000 * fontSize <= w
                        if (adding) {
                            words.removeFirst()
                            subLine = lineCandidate
                        }
                    } while(adding && words.isNotEmpty())
                    if (subLine != null) {
                        tmpLines.add(subLine)
                    }
                }
                x = 0f
            }
        }

        println()

        lines = tmpLines
        return tmpLines
    }

    override fun render(
        context: RenderContext,
        requestNewPage: (currentContext: RenderContext) -> RenderContext
    ): RenderContext {

        var currentContext = context
        var c = currentContext.content

        val xMin = getXMin(context)
        val xStart = context.startPosition.x + xMin
        val yStart = getYMin(context) - context.startPosition.y

        c.setFont(font, fontSize)

        val l = computeLines(SizeContext(startPosition = context.startPosition, parentContentWidth = getXMax(context) - getXMin(context)))

        if (l.isEmpty()) {
            return context
        }

        var (x, y) = if (newLine) {
            xMin to yStart - height
        } else {
            xStart to yStart
        }

        l.forEachIndexed { i, line ->
            println("line = $line")
            if (i > 0) {
                x = xMin
                y -= height
            }
            println("x = $x, y = $y")
            c.beginText()
            c.newLineAtOffset(x, y + yOffset)
            c.showText(line)
            c.endText()
        }

        x = if (l.size == 1 && !newLine) {
            font.getStringWidth(l.first()) / 1000 * fontSize + xStart
        } else {
            font.getStringWidth(l.last()) / 1000 * fontSize + xMin
        }

        println(x)

        context.startPosition.x += x - xStart
        context.startPosition.y += yStart - y

        return context
    }

    override fun endPosition(context: SizeContext): Position {
        val l = computeLines(context)
        val linesCount = l.size

        return if (linesCount < 1) {
            Position(
                x = context.startPosition.x,
                y = if (newLine) context.startPosition.y + height else context.startPosition.y,
            )
        } else if (newLine) {
            val maxWidth = l.maxOf { line -> font.getStringWidth(line) / 1000 * fontSize }
            Position(
                x = maxWidth,
                y = context.startPosition.y + height * linesCount,
            )
        } else {
            val maxWidth = l.mapIndexed { i, line ->
                if (i == 0) {
                    font.getStringWidth(line) / 1000 * fontSize + context.startPosition.x
                } else {
                    font.getStringWidth(line) / 1000 * fontSize
                }
            }.max()
            Position(
                x = maxWidth,
                y = context.startPosition.y + height * (linesCount - 1),
            )
        }
    }
}
