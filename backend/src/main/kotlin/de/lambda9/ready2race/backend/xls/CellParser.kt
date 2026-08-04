package de.lambda9.ready2race.backend.xls

import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.recoverDefault
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

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
                // TODO: maybe configurable, how strict/lax
                CellType.STRING -> input.stringCellValue.let{ value -> value.toDoubleOrNull()?.let { KIO.ok(it) } ?: KIO.fail(XLSReadError.CellError.ParseError.UnparsableStringValue(row, col, value)) }
                else -> KIO.fail(XLSReadError.CellError.ParseError.WrongCellType(row, col, input.cellType, CellType.NUMERIC))
            }
        }

        val string get() = CellParser<String> { input , row, col ->
            when (input.cellType) {
                CellType.STRING, CellType.BLANK -> KIO.ok(input.stringCellValue)
                else -> KIO.fail(XLSReadError.CellError.ParseError.WrongCellType(row, col, input.cellType, CellType.STRING))
            }
        }

        val uuid get() = CellParser<UUID> { input, row, col ->
            when (input.cellType) {
                CellType.BLANK -> KIO.fail(XLSReadError.CellError.ParseError.CellBlank(row, col))
                CellType.STRING -> input.stringCellValue.let { value ->
                    try {
                        KIO.ok(UUID.fromString(value.trim()))
                    } catch (e: IllegalArgumentException) {
                        KIO.fail(XLSReadError.CellError.ParseError.UnparsableStringValue(row, col, value))
                    }
                }
                else -> KIO.fail(XLSReadError.CellError.ParseError.WrongCellType(row, col, input.cellType, CellType.STRING))
            }
        }

        fun <A> maybe(parser: CellParser<A>) = CellParser { input, row, col ->
            parser.parse(input, row, col).recoverDefault { null }
        }

        /**
         * Datum-Spalte des Zeitstrahl-Imports. STRING: `d.M.` / `d.M.yyyy` / `dd.MM.yyyy`
         * (fehlendes Jahr -> [defaultYear]). NUMERIC: Excel speichert Datum/Zeit numerisch als
         * Tages-Seriennummer -> `localDateTimeCellValue` konvertiert das unabhängig vom
         * Zell-Zahlenformat korrekt in ein Datum.
         */
        fun localDate(defaultYear: Int) = CellParser<LocalDate> { input, row, col ->
            when (input.cellType) {
                CellType.BLANK -> KIO.fail(XLSReadError.CellError.ParseError.CellBlank(row, col))
                CellType.NUMERIC -> KIO.ok(input.localDateTimeCellValue.toLocalDate())
                CellType.STRING -> parseGermanDate(input.stringCellValue, defaultYear)?.let { KIO.ok(it) }
                    ?: KIO.fail(XLSReadError.CellError.ParseError.UnparsableStringValue(row, col, input.stringCellValue))

                else -> KIO.fail(XLSReadError.CellError.ParseError.WrongCellType(row, col, input.cellType, CellType.NUMERIC))
            }
        }

        /** Uhrzeit-Spalte des Zeitstrahl-Imports. STRING: `H:mm` / `HH:mm`. NUMERIC: siehe [localDate]. */
        val localTime get() = CellParser<LocalTime> { input, row, col ->
            when (input.cellType) {
                CellType.BLANK -> KIO.fail(XLSReadError.CellError.ParseError.CellBlank(row, col))
                CellType.NUMERIC -> KIO.ok(input.localDateTimeCellValue.toLocalTime())
                CellType.STRING -> parseGermanTime(input.stringCellValue)?.let { KIO.ok(it) }
                    ?: KIO.fail(XLSReadError.CellError.ParseError.UnparsableStringValue(row, col, input.stringCellValue))

                else -> KIO.fail(XLSReadError.CellError.ParseError.WrongCellType(row, col, input.cellType, CellType.NUMERIC))
            }
        }

        private fun parseGermanDate(raw: String, defaultYear: Int): LocalDate? {
            val parts = raw.trim().trimEnd('.').split(".").filter { it.isNotBlank() }
            if (parts.size !in 2..3) return null

            val day = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val year = if (parts.size == 3) parts[2].toIntOrNull() ?: return null else defaultYear

            return try {
                LocalDate.of(year, month, day)
            } catch (e: DateTimeException) {
                null
            }
        }

        private fun parseGermanTime(raw: String): LocalTime? {
            val parts = raw.trim().split(":")
            if (parts.size != 2) return null

            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null

            return try {
                LocalTime.of(hour, minute)
            } catch (e: DateTimeException) {
                null
            }
        }

    }
}