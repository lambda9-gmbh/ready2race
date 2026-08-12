package de.lambda9.ready2race.backend.app.participantRequirement.boundary

import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

/**
 * Baut die Liste der Gemeldeten, denen noch Bedingungen fehlen, als xlsx.
 *
 * Bewusst xlsx und nicht csv: die Datei wird per Doppelklick in Excel geöffnet, um daraus
 * Vereine anzuschreiben. Bei csv müssten Trennzeichen und Kodierung stimmen - genau daran ist
 * am 09.08.2026 schon der Import der DRV-Aktivenpassliste gescheitert.
 */
object OpenRequirementExport {

    const val COLUMN_CLUB = "Verein"
    const val COLUMN_LASTNAME = "Nachname"
    const val COLUMN_FIRSTNAME = "Vorname"
    const val COLUMN_YEAR = "Jahrgang"
    const val COLUMN_ROLE = "Rolle"
    const val COLUMN_EMAIL = "E-Mail"
    const val COLUMN_REGISTRANT_EMAIL = "E-Mail Meldender"
    const val COLUMN_COMPETITIONS = "Rennen"
    const val COLUMN_OPEN = "Fehlende Bedingungen"

    private val headers = listOf(
        COLUMN_CLUB, COLUMN_LASTNAME, COLUMN_FIRSTNAME, COLUMN_YEAR,
        COLUMN_ROLE, COLUMN_EMAIL, COLUMN_REGISTRANT_EMAIL, COLUMN_COMPETITIONS, COLUMN_OPEN,
    )

    private val columnWidths = listOf(38, 20, 20, 10, 16, 30, 30, 26, 34)

    data class Row(
        val club: String,
        val lastname: String,
        val firstname: String,
        val year: Int?,
        val roles: List<String>,
        val email: String?,
        /**
         * E-Mail der Person, die die Vereinsmeldung abgegeben hat - meist die einzige erreichbare
         * Adresse, weil die Athleten selbst selten eine hinterlegt haben.
         */
        val registrantEmail: String?,
        val competitions: List<String>,
        val openRequirements: List<String>,
    )

    /**
     * Sortiert nach Verein, dann Nachname, Vorname - angeschrieben wird vereinsweise.
     */
    fun sort(rows: List<Row>): List<Row> =
        rows.sortedWith(
            compareBy<Row, String>(String.CASE_INSENSITIVE_ORDER) { it.club }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.lastname }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.firstname }
        )

    fun build(rows: List<Row>): ByteArray = XSSFWorkbook().use { workbook ->
        val sheet = workbook.createSheet("Offene Bedingungen")

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

        sort(rows).forEachIndexed { idx, row ->
            val r = sheet.createRow(idx + 1)
            r.createCell(0).setCellValue(row.club)
            r.createCell(1).setCellValue(row.lastname)
            r.createCell(2).setCellValue(row.firstname)
            // Jahrgang als Zahl, damit sich in Excel danach sortieren lässt.
            row.year?.let { r.createCell(3).setCellValue(it.toDouble()) }
            r.createCell(4).setCellValue(row.roles.joinToString(", "))
            r.createCell(5).setCellValue(row.email ?: "")
            r.createCell(6).setCellValue(row.registrantEmail ?: "")
            r.createCell(7).setCellValue(row.competitions.joinToString(", "))
            r.createCell(8).setCellValue(row.openRequirements.joinToString(", "))
        }

        columnWidths.forEachIndexed { idx, width -> sheet.setColumnWidth(idx, width * 256) }
        sheet.createFreezePane(0, 1)

        ByteArrayOutputStream().use { out ->
            workbook.write(out)
            out.toByteArray()
        }
    }
}
