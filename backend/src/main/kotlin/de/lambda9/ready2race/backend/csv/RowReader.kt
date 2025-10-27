package de.lambda9.ready2race.backend.csv

import de.lambda9.ready2race.backend.parsing.Parser
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.recover

class RowReader(
    private val scope: KIO.ComprehensionScope<Any?, CSVReadError.CellError>,
    private val columns: Map<String, Int>,
    private val row: List<String>,
    private val rowNum: Int,
) : KIO.ComprehensionScope<Any?, CSVReadError.CellError> by scope {

    fun <A : Any> cell(header: String, parser: Parser<A>): IO<CSVReadError.CellError, A> =
        cellInternal(header, parser)

    fun cell(header: String): IO<CSVReadError.CellError, String> =
        cell(header) { it }

    fun <A> optionalCell(header: String?, parser: Parser<A & Any>): IO<CSVReadError.CellError, A?> =
        if (header == null) {
            KIO.ok(null)
        } else {
            cellInternal(header, parser).recover {
                when (it) {
                    is CSVReadError.CellError.ColumnUnknown, is CSVReadError.CellError.MissingValue -> KIO.ok(null)
                    else -> KIO.fail(it)
                }
            }
        }

    fun optionalCell(header: String?): IO<CSVReadError.CellError, String?> =
        optionalCell(header) { it }

    private fun <A : Any> cellInternal(header: String, parser: Parser<A>): IO<CSVReadError.CellError, A> =
        columns[header]?.let {

            val cell = row[it]

            if (cell.isBlank()) {
                KIO.fail(CSVReadError.CellError.MissingValue(rowNum, header))
            } else {
                parser(cell) { task ->
                    task.mapError { CSVReadError.CellError.UnparsableValue(rowNum, header, cell) }
                }
            }

        } ?: KIO.fail(CSVReadError.CellError.ColumnUnknown(header))

}