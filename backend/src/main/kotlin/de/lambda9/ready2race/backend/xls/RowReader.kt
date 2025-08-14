package de.lambda9.ready2race.backend.xls

import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.recover
import org.apache.poi.ss.usermodel.Row

class RowReader(
    private val scope: KIO.ComprehensionScope<Any?, XLSReadError.CellError>,
    private val columns: Map<String, Int>,
    private val row: Row,
) : KIO.ComprehensionScope<Any?, XLSReadError.CellError> by scope {

    fun <A> cell(header: String, parser: CellParser<A>): IO<XLSReadError.CellError, A> =
        cellInternal(header, parser)

    fun <A> optionalCell(header: String, parser: CellParser<A>): IO<XLSReadError.CellError, A?> =
        cellInternal(header, parser).recover {
            when (it) {
                is XLSReadError.CellError.ColumnUnknown, is XLSReadError.CellError.ParseError.CellBlank -> KIO.ok(null)
                else -> KIO.fail(it)
            }
        }

    private fun <A> cellInternal(header: String, parser: CellParser<A>): IO<XLSReadError.CellError, A> =
        columns[header]?.let {

            val cell = row.getCell(it)

            if (cell == null) {
                KIO.fail(XLSReadError.CellError.ParseError.CellBlank(row.rowNum, header))
            } else {
                parser.parse(cell, row.rowNum, header)
            }

        } ?: KIO.fail(XLSReadError.CellError.ColumnUnknown(header))
}