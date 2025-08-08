package de.lambda9.ready2race.backend.xls

import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.recover
import de.lambda9.tailwind.core.extensions.kio.recoverDefault
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType

fun interface CellParser<A> {

    fun parse(input: Cell, row: Int, col: String): IO<XLSReadError.CellError.ParseError, A>

    fun <B> map(f: (A) -> B) = let { p ->
        CellParser { input, row, col ->
            p.parse(input, row, col).map { f(it) }
        }
    }

    companion object {

        val int get() = numeric.map { it.toInt() }

        val numeric get() = CellParser<Double> { input, row, col ->
            when (input.cellType) {
                CellType.BLANK -> KIO.fail(XLSReadError.CellError.ParseError.CellBlank(row, col))
                CellType.NUMERIC -> KIO.ok(input.numericCellValue)
                else -> KIO.fail(XLSReadError.CellError.ParseError.WrongCellType(row, col, input.cellType, CellType.NUMERIC))
            }
        }

        val string get() = CellParser<String> { input , row, col ->
            when (input.cellType) {
                CellType.STRING, CellType.BLANK -> KIO.ok(input.stringCellValue)
                else -> KIO.fail(XLSReadError.CellError.ParseError.WrongCellType(row, col, input.cellType, CellType.STRING))
            }
        }

        fun <A> maybe(parser: CellParser<A>) = CellParser { input, row, col ->
            parser.parse(input, row, col).recoverDefault { null }
        }

    }
}