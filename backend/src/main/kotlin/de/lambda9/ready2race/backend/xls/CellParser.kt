package de.lambda9.ready2race.backend.xls

import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.recover
import de.lambda9.tailwind.core.extensions.kio.recoverDefault
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType

fun interface CellParser<A> {

    fun parse(input: Cell): IO<XLSReadError.CellError.ParseError, A>

    companion object {

        val numeric get() = CellParser<Double> {
            when (it.cellType) {
                CellType.BLANK -> KIO.fail(XLSReadError.CellError.ParseError.CellBlank)
                CellType.NUMERIC -> KIO.ok(it.numericCellValue)
                else -> KIO.fail(XLSReadError.CellError.ParseError.WrongCellType)
            }
        }

        val string get() = CellParser<String> {
            when (it.cellType) {
                CellType.STRING, CellType.BLANK -> KIO.ok(it.stringCellValue)
                else -> KIO.fail(XLSReadError.CellError.ParseError.WrongCellType)
            }
        }

        fun <A> maybe(parser: CellParser<A>) = CellParser {
            parser.parse(it).recoverDefault { null }
        }

    }
}