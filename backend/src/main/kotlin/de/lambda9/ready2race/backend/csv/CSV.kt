package de.lambda9.ready2race.backend.csv

import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVWriter
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import io.ktor.utils.io.charsets.forName
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset

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
        noHeader: Boolean = false,
        separator: Char = ',',
        charset: String = "UTF-8",
        reader: RowReader.() -> A
    ): IO<CSVReadError, List<A>> = KIO.comprehension {

        val cs = try {
            charset(charset)
        } catch (e: Exception) {
            Charsets.UTF_8
        }

        val csvParser = CSVParserBuilder()
            .withSeparator(separator)
            .build()

        val reader = CSVReaderBuilder(`in`.bufferedReader(cs))
            .withCSVParser(csvParser)
            .build()

        val result = mutableListOf<A>()
        var rowNum = 0

        val columns = if (noHeader) {
            val first = reader.peek()

            if (first == null) {
                return@comprehension KIO.ok(result)
            } else {
                first.mapIndexed { idx, _ ->
                    (idx + 1).toString() to idx
                }.toMap()
            }
        } else {
            val header = reader.readNext()

            !KIO.failOnNull(header) { CSVReadError.NoHeaders }
                .map {
                    it.mapIndexedNotNull { idx, item ->
                        item?.let { it to idx }
                    }.toMap()
                }
                .failIf({ it.isEmpty() }) { CSVReadError.NoHeaders }
        }


        val maxIndex = columns.maxOf { it.value }

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