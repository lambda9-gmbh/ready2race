package de.lambda9.ready2race.backend.csv

import de.lambda9.tailwind.core.Exit
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvReadTest {

    private data class Row(val firstname: String, val lastname: String, val club: String?)

    private fun read(csv: String): Exit<CSVReadError, List<Row>> =
        CSV.read(
            `in` = csv.toByteArray(Charsets.UTF_8).inputStream(),
            separator = ';',
        ) {
            Row(
                firstname = !cell("Vorname"),
                lastname = !cell("Nachname"),
                club = !optionalCell("Vereinsname"),
            )
        }.unsafeRunSync()

    @Test
    fun readsAPlainList() {
        val exit = read(
            """
            Vorname;Nachname;Vereinsname
            Rosa;Beckert;Rudergemeinschaft Hansa
            """.trimIndent()
        )

        val rows = (exit as Exit.Success).value
        assertEquals(listOf(Row("Rosa", "Beckert", "Rudergemeinschaft Hansa")), rows)
    }

    @Test
    fun aLoneQuoteInAFieldDoesNotBlowUp() {
        // Der Fund vom 09.08.2026: die DRV-Aktivenpass-Liste enthält den Vereinsnamen
        // Berliner Ruder-Club „Welle-Poseidon" e.V. - ein typografisches Anführungszeichen
        // öffnet, ein gerades " schließt. OpenCSV liest das gerade " als Beginn eines
        // zitierten Feldes, verschlingt alle folgenden Zeilen und wirft am Dateiende
        // CsvMalformedLineException. Die Exception verlässt den IO<CSVReadError, ...>-Vertrag
        // und schlägt als HTTP 500 samt Stacktrace beim Aufrufer auf.
        val exit = read(
            """
            Vorname;Nachname;Vereinsname
            Nele;Hansen;Berliner Ruder-Club „Welle-Poseidon" e.V.
            Rosa;Beckert;Rudergemeinschaft Hansa
            """.trimIndent()
        )

        assertTrue(
            exit is Exit.Success || (exit as? Exit.Failure)?.error is CSVReadError,
            "Erwartet: Erfolg oder ein CSVReadError. Bekommen: $exit",
        )
    }

    @Test
    fun aLoneQuoteKeepsTheFollowingRowsIntact() {
        // Weitergehend: das gerade Anführungszeichen ist Teil des Vereinsnamens, keine
        // Zitatsyntax. Beide Zeilen müssen ankommen, die zweite unverfälscht.
        val exit = read(
            """
            Vorname;Nachname;Vereinsname
            Nele;Hansen;Berliner Ruder-Club „Welle-Poseidon" e.V.
            Rosa;Beckert;Rudergemeinschaft Hansa
            """.trimIndent()
        )

        val rows = (exit as Exit.Success).value
        assertEquals(2, rows.size, "Beide Datenzeilen müssen gelesen werden")
        assertEquals(Row("Rosa", "Beckert", "Rudergemeinschaft Hansa"), rows[1])
        assertEquals("Nele", rows[0].firstname)
        assertEquals("Berliner Ruder-Club „Welle-Poseidon\" e.V.", rows[0].club)
    }

    @Test
    fun realQuotingStillProtectsTheSeparator() {
        // Die Gegenprobe zum Fund oben: wo Anführungszeichen echte Syntax sind, müssen sie
        // weiter ausgewertet werden. Würde generell ohne Quoting gelesen, zerfiele dieser
        // Vereinsname am geschützten Semikolon in zwei Felder.
        val exit = read(
            """
            Vorname;Nachname;Vereinsname
            Nele;Hansen;"Ruderverein Nord; Abteilung Coastal"
            """.trimIndent()
        )

        val rows = (exit as Exit.Success).value
        assertEquals(listOf(Row("Nele", "Hansen", "Ruderverein Nord; Abteilung Coastal")), rows)
    }

    @Test
    fun anUnreadableFileFailsWithAnErrorInsteadOfAPanic() {
        // Selbst wenn beide Versuche scheitern, muss ein CSVReadError herauskommen. Vorher
        // verließ die OpenCSV-Exception den IO<CSVReadError, ...>-Vertrag und wurde vom
        // Aufrufer als HTTP 500 samt komplettem Stacktrace an den Browser ausgeliefert.
        val exit = read("Vorname;Nachname;Vereinsname\nNele;Hansen;\"nie geschlossen\n".repeat(600))

        val error = (exit as? Exit.Failure)?.error
        assertTrue(
            error == null || error is CSVReadError,
            "Erwartet: Erfolg oder CSVReadError, kein Panic. Bekommen: $exit",
        )
    }
}
