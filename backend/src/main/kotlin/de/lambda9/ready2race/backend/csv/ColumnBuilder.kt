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

    fun overrideColumn(
        header: String,
        cellCondition: A.(index: Int) -> Boolean = { false },
        f: A.(index: Int) -> String,
    ) {
        val origin = columns.find { it.header == header }
        if (origin == null) {
            column(header, f)
        } else {
            columns.remove(origin)
            column(header) { index ->
                if (cellCondition(index)) {
                    f(index)
                } else {
                    origin.f(this, index)
                }
            }
        }
    }
}