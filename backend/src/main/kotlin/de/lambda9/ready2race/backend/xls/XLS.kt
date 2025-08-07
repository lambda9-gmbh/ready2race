package de.lambda9.ready2race.backend.xls

import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIOException
import de.lambda9.tailwind.core.extensions.kio.traverse
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

object XLS {

    fun <A> read(
        `in`: InputStream,
        reader: RowReader.() -> A
    ): IO<XLSReadError, List<A>> {

        val workbook = WorkbookFactory.create(`in`)
        val sheet = workbook.getSheetAt(0)

        val columns = sheet.firstOrNull()?.mapIndexedNotNull { idx, cell ->
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue!! to idx
                else -> null
            }
        }?.toMap() ?: emptyMap()

        if (columns.isEmpty()) {
            return KIO.fail(XLSReadError.NoHeaders)
        }

        return sheet.drop(1).traverse { row ->
            KIO.effect {
                RowReader(columns, row).reader()
            }.mapError { t ->
                when (t) {
                    is KIOException -> t.error.fold(
                        onExpected = { err -> err as? XLSReadError ?: throw t },
                        onPanic = { throw it }
                    )
                    else -> throw t
                }
            }
        }
    }

    fun foo() {

        val istr = ByteArray(0).inputStream()

        val r = read(istr) {

            val bar = cell("col1", CellParser {
                when (it.cellType)
                {
                    CellType._NONE -> TODO()
                    CellType.NUMERIC -> TODO()
                    CellType.STRING -> TODO()
                    CellType.FORMULA -> TODO()
                    CellType.BLANK -> TODO()
                    CellType.BOOLEAN -> TODO()
                    CellType.ERROR -> TODO()
                }
            })
        }

    }
}