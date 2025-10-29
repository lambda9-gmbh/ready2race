package de.lambda9.ready2race.backend.csv

import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVWriter
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import java.io.InputStream
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

    fun <A> read(
        `in`: InputStream,
        separator: Char = ',',
        reader: RowReader.() -> A
    ): IO<CSVReadError, List<A>> = KIO.comprehension {

        val csvParser = CSVParserBuilder()
            .withSeparator(separator)
            .build()

        val reader = CSVReaderBuilder(`in`.bufferedReader())
            .withCSVParser(csvParser)
            .build()

        val header = reader.readNext()

        val columns = !KIO.failOnNull(header) { CSVReadError.NoHeaders }
            .map {
                it.mapIndexedNotNull { idx, item ->
                    item?.let { it to idx }
                }.toMap()
            }
            .failIf({ it.isEmpty() }) { CSVReadError.NoHeaders }

        val maxIndex = columns.maxOf { it.value }

        val result = mutableListOf<A>()
        var rowNum = 0

        do {
            val row = reader.readNext()
            if (row != null) {
                if (row.size <= maxIndex) {
                    return@comprehension KIO.fail(CSVReadError.MalformedData)
                }
                rowNum++
                val value = !KIO.comprehension {
                    KIO.ok(RowReader(this, columns, row.toList(), rowNum).reader())
                }
                result.add(value)
            }
        } while (row != null)

        KIO.ok(result)
    }
}