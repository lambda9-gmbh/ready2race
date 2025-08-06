package de.lambda9.ready2race.backend.pdf.elements.table

import de.lambda9.ready2race.backend.pdf.BlockBuilder
import de.lambda9.ready2race.backend.pdf.Padding
import java.awt.Color

class RowBuilder(
    private val columns: List<Column>,
    private val rowColor: Color?,
    private val withBorder: Boolean,
) {

    internal val cells: MutableList<Cell> = mutableListOf()

    fun cell(
        padding: Padding = Padding(2f, 1f),
        color: Color? = null,
        builder: BlockBuilder.() -> Unit
    ) {

        if (cells.size >= columns.size) {
            throw IllegalStateException("More cells in row than columns in table")
        }

        val children = BlockBuilder().apply(builder).children
        cells.add(
            Cell(
                width = columns[cells.size].width,
                color = color ?: rowColor,
                withBorder = withBorder,
                padding = padding,
                children = children,
            )
        )
    }

}