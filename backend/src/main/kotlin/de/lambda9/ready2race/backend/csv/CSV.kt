package de.lambda9.ready2race.backend.csv

import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVWriter
import com.opencsv.exceptions.CsvException
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import io.ktor.utils.io.charsets.forName
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset

object CSV {

    fun <A: Any> write(
        out: OutputStream,
        data: List<A>,
        /**
         * Some tooling imports the header row as a data row unless told otherwise (RaceClocker does),
         * which leaves a bogus participant behind. Such exports are written without one; the columns are
         * then mapped by position on the receiving side.
         */
        writeHeader: Boolean = true,
        builder: ColumnBuilder<A>.() -> Unit,
    ) {
        val columns = ColumnBuilder<A>().apply(builder).columns

        OutputStreamWriter(out).use { writer ->
            val csvWriter = CSVWriter(writer)

            if (writeHeader) {
                val header = columns.map { it.header }
                csvWriter.writeNext(header.toTypedArray())
            }

            data.forEachIndexed { index, item ->
                val row = columns.map { it.f(item, index) }
                csvWriter.writeNext(row.toTypedArray())
            }

            writer.flush()
        }
    }

    /**
     * Ergebnis eines Parser-Durchlaufs. [Malformed] steht für einen Abbruch von OpenCSV -
     * praktisch immer ein Anführungszeichen, das nie geschlossen wird.
     */
    private sealed interface Rows {
        data class Ok(val rows: List<Array<String>>) : Rows
        data class Malformed(val detail: String?) : Rows
    }

    /**
     * Liest die ganze Datei mit einem frisch gebauten Parser. Frisch ist wichtig: der Parser
     * merkt sich ein offenes Anführungszeichen über Zeilen hinweg, ein wiederverwendeter
     * würde den zweiten Durchlauf bereits "in Anführungszeichen" beginnen.
     */
    private fun parseRows(
        bytes: ByteArray,
        cs: Charset,
        separator: Char,
        ignoreQuotations: Boolean,
    ): Rows = try {
        val parser = CSVParserBuilder()
            .withSeparator(separator)
            .withIgnoreQuotations(ignoreQuotations)
            .build()

        CSVReaderBuilder(bytes.inputStream().bufferedReader(cs))
            .withCSVParser(parser)
            .build()
            .use { reader -> Rows.Ok(generateSequence { reader.readNext() }.toList()) }
    } catch (e: CsvException) {
        Rows.Malformed(e.message)
    } catch (e: IOException) {
        Rows.Malformed(e.message)
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

        // Einmal komplett einlesen: der zweite Versuch unten braucht denselben Inhalt, ein
        // InputStream lässt sich aber nur einmal durchlaufen.
        val bytes = try {
            `in`.use { it.readBytes() }
        } catch (e: IOException) {
            return@comprehension KIO.fail(CSVReadError.FileError)
        }

        // Erst streng lesen, damit echtes Quoting erhalten bleibt - ein "a;b" schützt sein
        // Semikolon und darf nicht zu zwei Feldern zerfallen.
        //
        // Scheitert das, wird ohne Anführungszeichen-Auswertung erneut gelesen. Auslöser dafür
        // sind Exporte, die ein einzelnes gerades Anführungszeichen mitten im Feld führen -
        // die DRV-Aktivenpassliste etwa enthält den Verein
        // Berliner Ruder-Club „Welle-Poseidon" e.V. Für OpenCSV beginnt dort ein zitiertes
        // Feld, das bis zum Dateiende offen bleibt. Eine Datei, die streng nicht lesbar ist,
        // ist ohnehin nicht regelkonform; sie danach wörtlich zu lesen ist besser, als den
        // ganzen Import scheitern zu lassen. Keine der beiden Einstellungen kann beide Fälle,
        // deshalb die Reihenfolge und nicht eine einzelne Konfiguration.
        val rows = when (val strict = parseRows(bytes, cs, separator, ignoreQuotations = false)) {
            is Rows.Ok -> strict.rows
            is Rows.Malformed -> when (val lenient = parseRows(bytes, cs, separator, true)) {
                is Rows.Ok -> lenient.rows
                is Rows.Malformed -> return@comprehension KIO.fail(
                    CSVReadError.MalformedQuotes(lenient.detail ?: strict.detail)
                )
            }
        }

        val result = mutableListOf<A>()

        val columns = if (noHeader) {
            val first = rows.firstOrNull()

            if (first == null) {
                return@comprehension KIO.ok(result)
            } else {
                first.mapIndexed { idx, _ ->
                    (idx + 1).toString() to idx
                }.toMap()
            }
        } else {
            !KIO.failOnNull(rows.firstOrNull()) { CSVReadError.NoHeaders }
                .map {
                    it.mapIndexedNotNull { idx, item ->
                        item?.let { it to idx }
                    }.toMap()
                }
                .failIf({ it.isEmpty() }) { CSVReadError.NoHeaders }
        }

        val maxIndex = columns.maxOf { it.value }
        val dataRows = if (noHeader) rows else rows.drop(1)

        dataRows.forEachIndexed { idx, row ->
            if (row.size <= maxIndex) {
                return@comprehension KIO.fail(CSVReadError.MalformedData)
            }
            val value = !KIO.comprehension {
                KIO.ok(RowReader(this, columns, row.toList(), idx + 1).reader())
            }
            result.add(value)
        }

        KIO.ok(result)
    }
}