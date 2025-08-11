package de.lambda9.ready2race.backend.pdf.elements.text

import de.lambda9.ready2race.backend.pdf.*
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import kotlin.streams.toList

data class Text(
    val newLine: Boolean,
    val centered: Boolean,
    val content: String,
    val fontSize: Float,
    val lineHeight: Float,
    val fontStyle: FontStyle,
    override val padding: Padding,
) : Element {

    companion object {

        private fun String.sanitizeNonPrintable() = codePoints()
            .toList()
            .map { codePoint ->
                when (codePoint) {
                    // Replace various Unicode spaces with regular space
                    0x00A0, // Non-breaking space
                    0x2002, // En space
                    0x2003, // Em space
                    0x2004, // Three-per-em space
                    0x2005, // Four-per-em space
                    0x2006, // Six-per-em space
                    0x2007, // Figure space
                    0x2008, // Punctuation space
                    0x2009, // Thin space
                    0x200A, // Hair space
                    0x202F, // Narrow no-break space
                    0x205F, // Medium mathematical space
                    0x3000  // Ideographic space
                        -> 0x0020 // Regular space

                    else -> codePoint
                }
            }
            .filter {
                !Character.isISOControl(it) &&
                    Character.UnicodeBlock.of(it) != null &&
                    it !in 0x200E..0x206F
            }
            .joinToString("") { Character.toString(it) }
    }


    private val sanitizedContent = content.sanitizeNonPrintable()

    private val font = when (fontStyle) {
        FontStyle.NORMAL -> PDType1Font(Standard14Fonts.FontName.HELVETICA)
        FontStyle.BOLD -> PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        FontStyle.ITALIC -> PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)
        FontStyle.BOLD_ITALIC -> PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE)
    }

    private val width = font.getStringWidth(sanitizedContent) / 1000 * fontSize
    private val height = lineHeight * fontSize * font.fontDescriptor.capHeight / 1000

    private val yOffset = fontSize / 4f

    private var lines: List<String>? = null

    private fun computeLines(context: SizeContext): List<String> {

        if (lines != null) {
            return lines!!
        }

        val tmpLines: MutableList<String> = mutableListOf()

        val xMax = context.parentContentWidth

        var x = if (newLine) {
            0f
        } else {
            context.startPosition.x
        }

        if (x + width <= xMax) {
            tmpLines.add(sanitizedContent)
        } else {
            val words = sanitizedContent.split("""\s""".toRegex()).toMutableList()
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
                            words.removeAt(0)
                            subLine = lineCandidate
                        }
                    } while (adding && words.isNotEmpty())
                    if (subLine != null) {
                        tmpLines.add(subLine)
                    }
                }
                x = 0f
            }
        }

        lines = tmpLines
        return tmpLines
    }

    override fun render(
        context: RenderContext,
        requestNewPage: (currentContext: RenderContext) -> RenderContext
    ): RenderContext {

        var currentContext = context
        var c = currentContext.content

        var xStart = currentContext.startPosition.x + getXMin(currentContext)
        var yStart = getYMin(currentContext) - currentContext.startPosition.y

        c.setFont(font, fontSize)

        val l = computeLines(
            SizeContext(
                startPosition = currentContext.startPosition,
                parentContentWidth = getXMax(currentContext) - getXMin(currentContext)
            )
        )

        if (l.isEmpty()) {
            return currentContext
        }

        var (x, y) = if (newLine) {
            getXMin(currentContext) to yStart - height
        } else {
            xStart to yStart
        }

        l.forEachIndexed { i, line ->

            if (i > 0) {
                x = getXMin(currentContext)
                y -= height
            }

            if (y < currentContext.parentsPadding.bottom - yOffset) {
                currentContext = requestNewPage(currentContext)
                xStart = getXMin(currentContext)
                x = xStart
                yStart = getYMin(currentContext)
                y = yStart - height
                c = currentContext.content
                c.setFont(font, fontSize)
            }

            if (centered) {
                val space = getXMax(currentContext) - x
                val lineWidth = fontSize * font.getStringWidth(line) / 1000
                x = x + (space - lineWidth) / 2
            }

            c.beginText()
            c.newLineAtOffset(x, y + yOffset)
            c.showText(line.sanitizeNonPrintable())
            c.endText()
        }

        x = if (l.size == 1 && !newLine) {
            font.getStringWidth(l.first()) / 1000 * fontSize + xStart
        } else {
            font.getStringWidth(l.last()) / 1000 * fontSize + getXMin(currentContext)
        }

        currentContext.startPosition.x += x - xStart
        currentContext.startPosition.y += yStart - y

        return currentContext
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
