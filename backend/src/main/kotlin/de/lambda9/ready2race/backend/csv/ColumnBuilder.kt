package de.lambda9.ready2race.backend.csv

class ColumnBuilder<A: Any> {

    internal val columns: MutableList<Column<A>> = mutableListOf()

    fun column(
        header: String,
        f: A.(index: Int) -> String,
    ) {
        columns.add(Column(header, f))
    }

    fun optionalColumn(
        header: String?,
        f: A.(index: Int) -> String,
    ) = header?.let { column(it, f) } ?: Unit
}