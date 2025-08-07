package de.lambda9.ready2race.backend.xls

import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrThrow
import de.lambda9.tailwind.core.extensions.kio.recover
import org.apache.poi.ss.usermodel.Row

class RowReader(
    private val columns: Map<String, Int>,
    private val row: Row,
) {

    fun <A> cell(header: String, parser: CellParser<A>): A =
        cellInternal(header, parser).unsafeRunSync().getOrThrow()

    fun <A> optionalCell(header: String, parser: CellParser<A>): A? =
        cellInternal(header, parser).recover {
            when (it) {
                XLSReadError.CellError.ColumnUnknown, XLSReadError.CellError.ParseError.CellBlank -> KIO.ok(null)
                XLSReadError.CellError.ParseError.WrongCellType -> KIO.fail(it)
            }
        }.unsafeRunSync().getOrThrow()

    private fun <A> cellInternal(header: String, parser: CellParser<A>): IO<XLSReadError.CellError, A> =
        columns[header]?.let {

            val cell = row.getCell(it)
            parser.parse(cell)

        } ?: KIO.fail(XLSReadError.CellError.ColumnUnknown)
}