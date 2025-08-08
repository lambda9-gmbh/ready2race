package de.lambda9.ready2race.backend.xls

import org.apache.poi.ss.usermodel.CellType

sealed interface XLSReadError {

    data object FileError : XLSReadError
    data object NoHeaders : XLSReadError

    sealed interface CellError : XLSReadError {

        data class ColumnUnknown(val expected: String) : CellError

        sealed interface ParseError : CellError {
            data class CellBlank(val row: Int, val col: String) : ParseError
            data class WrongCellType(val row: Int, val col: String, val actual: CellType, val expected: CellType) : ParseError
        }

    }
}