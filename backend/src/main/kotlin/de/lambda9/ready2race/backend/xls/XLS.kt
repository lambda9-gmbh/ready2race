package de.lambda9.ready2race.backend.xls

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

object XLS {

    fun <A> read(
        `in`: InputStream,
        mapper: (Row) -> A
    ): List<A> {

        val workbook = WorkbookFactory.create(`in`)
        val sheet = workbook.getSheetAt(0)

        val row = sheet.getRow(0)

        row.cellIterator().

        return sheet.drop(1).map(mapper)
    }

}