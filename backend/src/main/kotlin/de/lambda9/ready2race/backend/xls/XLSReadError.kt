package de.lambda9.ready2race.backend.xls

sealed interface XLSReadError {

    data object NoHeaders : XLSReadError

    sealed interface CellError : XLSReadError {

        data object ColumnUnknown : CellError

        enum class ParseError : CellError {
            CellBlank,
            WrongCellType
        }

    }
}