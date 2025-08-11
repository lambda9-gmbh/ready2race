package de.lambda9.ready2race.backend.xls

import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.traverse
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

object XLS {

    fun <A> read(
        `in`: InputStream,
        reader: RowReader.() -> A
    ): IO<XLSReadError, List<A>> = KIO.comprehension {

        val sheet = !KIO.effect {
            val workbook = WorkbookFactory.create(`in`)
            workbook.getSheetAt(0)
        }.mapError { XLSReadError.FileError }

        val columns = sheet.firstOrNull()?.mapIndexedNotNull { idx, cell ->
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue!! to idx
                else -> null
            }
        }?.toMap() ?: emptyMap()

        if (columns.isEmpty()) {
            KIO.fail(XLSReadError.NoHeaders)
        } else {
            sheet.drop(1).traverse { row ->
                KIO.comprehension<Any?, XLSReadError.CellError, A> {
                    KIO.ok(RowReader(this, columns, row).reader())
                }
            }
        }
    }
}