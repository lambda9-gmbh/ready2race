package de.lambda9.ready2race.backend.csv

import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import de.lambda9.ready2race.backend.calls.responses.ToApiError
import io.ktor.http.HttpStatusCode

sealed interface CSVReadError : ToApiError {

    data object FileError : CSVReadError
    data object NoHeaders : CSVReadError

    data object MalformedData : CSVReadError

    sealed interface CellError : CSVReadError {

        data class ColumnUnknown(val expected: String) : CellError
        data class MissingValue(val row: Int, val col: String) : CellError
        data class UnparsableValue(val row: Int, val col: String, val value: String) : CellError
    }

    override fun respond(): ApiError = when (this) {
        FileError -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Cannot read given file",
            errorCode = ErrorCode.FILE_ERROR
        )

        NoHeaders -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Cannot find column headers, expected them in first row.",
            errorCode = ErrorCode.SPREADSHEET_NO_HEADERS
        )

        MalformedData -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Cannot read content, rows have different number of values.",
            errorCode = ErrorCode.SPREADSHEET_MALFORMED,
        )

        is CellError.ColumnUnknown -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Required column '$expected' is missing",
            errorCode = ErrorCode.SPREADSHEET_COLUMN_UNKNOWN,
            details = mapOf("expected" to expected)
        )

        is CellError.MissingValue -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Required value in row $row and column '$col' is missing.",
            errorCode = ErrorCode.SPREADSHEET_CELL_BLANK,
            details = mapOf("row" to row, "column" to col)
        )

        is CellError.UnparsableValue -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Cannot parse '$value' in row $row and column '$col'.",
            errorCode = ErrorCode.SPREADSHEET_UNPARSABLE_STRING,
            details = mapOf("row" to row, "column" to col, "value" to value)
        )
    }
}