package de.lambda9.ready2race.backend.app.eventSchedule

import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ScheduleImportTemplate
import de.lambda9.ready2race.backend.xls.CellParser
import de.lambda9.ready2race.backend.xls.XLS
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrNull
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Die Beispieldatei ist nur dann eine Hilfe, wenn der Import sie auch lesen kann. Der Test liest sie
 * deshalb mit genau den Spalten und Parsern zurück, die [de.lambda9.ready2race.backend.app.eventSchedule.boundary.EventScheduleService.importSchedule]
 * verwendet - eine umbenannte Spalte oder ein falsch formatiertes Datum fällt hier auf.
 */
class ScheduleImportTemplateTest {

    private data class Row(
        val date: LocalDate,
        val time: LocalTime,
        val competition: String?,
        val match: String,
        val duration: Int?,
    )

    private fun readTemplate(exampleDate: LocalDate): List<Row> {
        val bytes = ScheduleImportTemplate.build(exampleDate)
        val exit = XLS.read(bytes.inputStream()) {
            Row(
                date = !cell(ScheduleImportTemplate.COLUMN_DATE, CellParser.localDate(exampleDate.year)),
                time = !cell(ScheduleImportTemplate.COLUMN_TIME, CellParser.localTime),
                competition = !optionalCell(ScheduleImportTemplate.COLUMN_COMPETITION, CellParser.string),
                match = !cell(ScheduleImportTemplate.COLUMN_MATCH, CellParser.string),
                duration = !optionalCell(ScheduleImportTemplate.COLUMN_DURATION, CellParser.int),
            )
        }.unsafeRunSync()

        return assertNotNull(exit.getOrNull(), "Beispieldatei muss mit den Import-Spalten lesbar sein")
    }

    @Test
    fun exampleFileIsReadableWithTheColumnsTheImportExpects() {
        val rows = readTemplate(LocalDate.of(2026, 8, 14))

        assertEquals(4, rows.size)
        rows.forEach { assertEquals(LocalDate.of(2026, 8, 14), it.date) }
        assertEquals(LocalTime.of(9, 0), rows.first().time)
        assertEquals(20, rows[1].duration)
    }

    @Test
    fun rowsWithoutCompetitionStayEmptySoTheImportTreatsThemAsFreeSlots() {
        val rows = readTemplate(LocalDate.of(2026, 8, 14))

        assertNull(rows.first().competition)
        assertEquals("Obleute-Besprechung", rows.first().match)
    }

    @Test
    fun rowsWithCompetitionCarryBothTheCompetitionAndTheMatchName() {
        val rows = readTemplate(LocalDate.of(2026, 8, 14))
        val linked = rows.filter { it.competition != null }

        assertEquals(listOf("1", "CF 2x"), linked.map { it.competition })
        assertEquals(listOf("Vorlauf 1", "Finale A"), linked.map { it.match })
    }
}
