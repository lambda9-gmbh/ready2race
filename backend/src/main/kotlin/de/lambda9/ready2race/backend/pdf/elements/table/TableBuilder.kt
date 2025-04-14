package de.lambda9.ready2race.backend.pdf.elements.table

import de.lambda9.ready2race.backend.pdf.Padding
import java.awt.Color

class TableBuilder(private val withBorder: Boolean) {

    private val columns: MutableList<Column> = mutableListOf()
    internal val rows: MutableList<Row> = mutableListOf()

    fun column(
        width: Float,
    ) {
        columns.add(
            Column(
                width = width
            )
        )
    }

    fun row(
        padding: Padding = Padding(0F),
        color: Color? = null,
        builder: RowBuilder.() -> Unit
    ) {
        val cells = RowBuilder(columns, color, withBorder).apply(builder).cells
        rows.add(
            Row(
                children = cells,
                padding = padding,
            )
        )
    }

}