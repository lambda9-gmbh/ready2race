package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Beispieldatei für den Excel-Import des Zeitstrahls: dieselben Spalten, die
 * [EventScheduleService.importSchedule] liest, gefüllt mit ein paar Zeilen, die alle Fälle zeigen
 * (verknüpft über Rennnummer, verknüpft über Kurzbezeichnung, freier Slot ohne Wettkampf).
 *
 * Die Kopfzeile ist bewusst hier definiert und nicht dupliziert: [ScheduleImportTemplateTest] prüft
 * sie gegen die Spaltennamen, die der Import erwartet.
 */
object ScheduleImportTemplate {

    /** Spalten, die der Import liest — exakt diese Schreibweise erwartet [EventScheduleService.importSchedule]. */
    const val COLUMN_DATE = "Datum"
    const val COLUMN_TIME = "Uhrzeit"
    const val COLUMN_COMPETITION = "Wettkampf"
    const val COLUMN_MATCH = "Lauf"
    const val COLUMN_DURATION = "Dauer"

    /** Rein informative Spalte — der Import ignoriert unbekannte Spalten. */
    private const val COLUMN_NOTE = "Hinweis"

    private val headers = listOf(
        COLUMN_DATE,
        COLUMN_TIME,
        COLUMN_COMPETITION,
        COLUMN_MATCH,
        COLUMN_DURATION,
        COLUMN_NOTE,
    )

    private val columnWidths = listOf(12, 10, 14, 22, 8, 62)

    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** Eine Beispielzeile; `competition == null` steht für einen freien Slot ohne Wettkampfbezug. */
    private data class ExampleRow(
        val time: String,
        val competition: String?,
        val match: String,
        val duration: Int,
        val note: String,
    )

    private val exampleRows = listOf(
        ExampleRow(
            time = "09:00",
            competition = null,
            match = "Obleute-Besprechung",
            duration = 30,
            note = "Ohne Wettkampf wird die Zeile ein freier Programmpunkt — der Text aus \"Lauf\" ist sein Name.",
        ),
        ExampleRow(
            time = "10:00",
            competition = "1",
            match = "Vorlauf 1",
            duration = 20,
            note = "\"Wettkampf\" darf die Rennnummer, die Kurzbezeichnung oder den Namen des Wettkampfs enthalten.",
        ),
        ExampleRow(
            time = "10:20",
            competition = "CF 2x",
            match = "Finale A",
            duration = 20,
            note = "\"Lauf\" muss dem Namen der Setup-Zeile entsprechen; Groß-/Kleinschreibung ist egal.",
        ),
        ExampleRow(
            time = "12:00",
            competition = null,
            match = "Regattapause",
            duration = 60,
            note = "\"Dauer\" ist optional und wird in Minuten angegeben.",
        ),
    )

    /**
     * Baut die Beispieldatei. [exampleDate] datiert alle Zeilen, damit die Vorlage im Zeitraum der
     * jeweiligen Veranstaltung liegt statt in einem beliebigen Beispieljahr.
     */
    fun build(exampleDate: LocalDate): ByteArray = XSSFWorkbook().use { workbook ->
        val sheet = workbook.createSheet("Zeitstrahl")

        val headerStyle = workbook.createCellStyle().apply {
            setFont(workbook.createFont().apply { bold = true })
            alignment = HorizontalAlignment.LEFT
        }

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { idx, header ->
            headerRow.createCell(idx).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }

        exampleRows.forEachIndexed { idx, example ->
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(exampleDate.format(dateFormat))
            row.createCell(1).setCellValue(example.time)
            // Freie Zeilen lassen die Wettkampf-Zelle leer - genau das erkennt der Import als freien Slot.
            example.competition?.let { row.createCell(2).setCellValue(it) }
            row.createCell(3).setCellValue(example.match)
            row.createCell(4).setCellValue(example.duration.toDouble())
            row.createCell(5).setCellValue(example.note)
        }

        columnWidths.forEachIndexed { idx, width -> sheet.setColumnWidth(idx, width * 256) }
        sheet.createFreezePane(0, 1)

        ByteArrayOutputStream().use { out ->
            workbook.write(out)
            out.toByteArray()
        }
    }
}
