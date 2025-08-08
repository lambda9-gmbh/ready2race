package de.lambda9.ready2race.backend.csv

import com.opencsv.CSVWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

object CSV {

    fun <A: Any> write(
        out: OutputStream,
        data: List<A>,
        builder: ColumnBuilder<A>.() -> Unit,
    ) {
        val columns = ColumnBuilder<A>().apply(builder).columns

        OutputStreamWriter(out).use { writer ->
            val csvWriter = CSVWriter(writer)

            val header = columns.map { it.header }
            csvWriter.writeNext(header.toTypedArray())

            data.forEachIndexed { index, item ->
                val row = columns.map { it.f(item, index) }
                csvWriter.writeNext(row.toTypedArray())
            }

            writer.flush()
        }
    }
}