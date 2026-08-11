package de.lambda9.ready2race.backend.raceclocker

import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerResultsXls
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Der Parser fürs „Results"-Blatt, geprüft an einem echten RaceClocker-Export (TEST CRF 2026 Läufe
 * Sonntag, 10.08.2026 von raceclocker.com/7aa7e86d). Die Datei ist ein Bericht mit doppelten
 * „Rank"-Spalten und Wellen-Titelzeilen — genau die Fälle, an denen der generische Import scheitert.
 */
class RaceClockerResultsXlsTest {

    private fun parse(): List<de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow> {
        val bytes = javaClass.getResourceAsStream("/raceclocker/results-sonntag.xlsx")!!.readBytes()
        val result = RaceClockerResultsXls.parse(bytes)
        assertTrue(result is RaceClockerResultsXls.ParseResult.Ok, "Datei konnte nicht gelesen werden")
        return (result as RaceClockerResultsXls.ParseResult.Ok).rows
    }

    @Test
    fun titleRowsAreSkippedAndEveryBoatCarriesItsIds() {
        val rows = parse()
        // Drei Wellen (5 + 5 + 7 Boote), die drei Wellen-Titelzeilen dazwischen fallen weg.
        assertEquals(17, rows.size, "Titelzeilen wurden nicht als solche erkannt")
        assertTrue(rows.all { it.ids.isNotEmpty() }, "Eine Zeile ohne R2R-Kennung durchgelassen")
    }

    @Test
    fun theReportRankColumnsDoNotBecomeResults() {
        // Der geklammerte Zwischen-Rank ("(1)") darf nicht als Ergebnis gelesen werden - der Parser
        // nimmt allein die eindeutige "Result"-Spalte.
        val first = parse().first()
        assertEquals("0:04:07.6", first.time, "Result-Zeit falsch aus dem Tagesbruchteil gebildet")
    }

    @Test
    fun eliminationCodesArriveAsSuch() {
        val rows = parse()
        val dq = rows.first { it.ids.contains(UUID.fromString("4037c68e-80d5-4313-b575-2620d1840468")) }
        assertEquals("DQ", dq.noResultReason, "DQ nicht als Ausscheidung erkannt")

        val dns = rows.first { it.ids.contains(UUID.fromString("863e4b34-6ed0-4622-bc19-121a1e46f62d")) }
        assertEquals("DNS", dns.noResultReason)
    }

    @Test
    fun bothUuidsOfTheExtraInfoCellAreCollected() {
        // "Extra info" trägt `<WellenID>: <matchTeamId>;` - der Aufrufer entscheidet über die
        // bekannten Ids des Laufs, welche zählt. Der Parser sammelt beide.
        val dq = parse().first { it.ids.contains(UUID.fromString("4037c68e-80d5-4313-b575-2620d1840468")) }
        assertTrue(dq.ids.contains(UUID.fromString("2f952b28-67da-4e68-80c9-2c0e568cf550")), "WellenID fehlt")
    }
}
