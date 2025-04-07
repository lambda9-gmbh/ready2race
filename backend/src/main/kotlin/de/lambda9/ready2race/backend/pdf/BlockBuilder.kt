package de.lambda9.ready2race.backend.pdf

import de.lambda9.ready2race.backend.pdf.elements.block.Block
import de.lambda9.ready2race.backend.pdf.elements.table.Table
import de.lambda9.ready2race.backend.pdf.elements.table.TableBuilder
import de.lambda9.ready2race.backend.pdf.elements.text.Text

class BlockBuilder {

    internal val children: MutableList<Element> = mutableListOf()

    private var inline = false

    fun text(
        fontStyle: FontStyle = FontStyle.NORMAL,
        fontSize: Float = 10F,
        lineHeight: Float = 1.8F,
        newLine: Boolean = true,
        content: () -> String,
    ) {
        children.add(
            Text(
                fontStyle = fontStyle,
                content = content(),
                lineHeight = lineHeight,
                fontSize = fontSize,
                padding = Padding(0F),
                newLine = newLine || !inline,
            )
        )
        inline = true
    }

    fun table(
        padding: Padding = Padding(0F),
        builder: TableBuilder.() -> Unit = {}
    ) {
        val rows = TableBuilder().apply(builder).rows
        children.add(
            Table(
                children = rows,
                padding = padding,
            )
        )
        inline = false
    }

    fun block(
        keepTogether: Boolean = false,
        padding: Padding = Padding(0F),
        builder: BlockBuilder.() -> Unit = {},
    ) {
        val blockChildren = BlockBuilder().apply(builder).children
        children.add(
            Block(
                keepTogether = keepTogether,
                children = blockChildren,
                padding = padding,
            )
        )
        inline = false
    }
}