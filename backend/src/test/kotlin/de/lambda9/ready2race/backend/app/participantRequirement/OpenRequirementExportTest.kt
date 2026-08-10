package de.lambda9.ready2race.backend.app.participantRequirement

import de.lambda9.ready2race.backend.app.participantRequirement.boundary.OpenRequirementExport
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenRequirementExportTest {

    private fun row(
        club: String = "Ruderklub Flensburg e.V.",
        lastname: String = "Beckert",
        firstname: String = "Rosa",
        year: Int? = 1999,
        roles: List<String> = listOf("Senior:in"),
        email: String? = "rosa@example.org",
        competitions: List<String> = listOf("11 CF1x"),
        open: List<String> = listOf("Aktivenpass"),
    ) = OpenRequirementExport.Row(club, lastname, firstname, year, roles, email, competitions, open)

    /** Liest die erzeugte Mappe zurück - Kopfzeile plus alle Datenzeilen als Text. */
    private fun read(rows: List<OpenRequirementExport.Row>): List<List<String>> =
        XSSFWorkbook(OpenRequirementExport.build(rows).inputStream()).use { wb ->
            val sheet = wb.getSheetAt(0)
            (0..sheet.lastRowNum).map { r ->
                val row = sheet.getRow(r)
                (0 until 8).map { c ->
                    val cell = row?.getCell(c) ?: return@map ""
                    when (cell.cellType) {
                        org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                            cell.numericCellValue.toInt().toString()
                        else -> cell.stringCellValue
                    }
                }
            }
        }

    @Test
    fun theHeaderNamesTheColumnsInOrder() {
        val header = read(listOf(row())).first()

        assertEquals(
            listOf(
                OpenRequirementExport.COLUMN_CLUB,
                OpenRequirementExport.COLUMN_LASTNAME,
                OpenRequirementExport.COLUMN_FIRSTNAME,
                OpenRequirementExport.COLUMN_YEAR,
                OpenRequirementExport.COLUMN_ROLE,
                OpenRequirementExport.COLUMN_EMAIL,
                OpenRequirementExport.COLUMN_COMPETITIONS,
                OpenRequirementExport.COLUMN_OPEN,
            ),
            header,
        )
    }

    @Test
    fun aRowCarriesEveryField() {
        val data = read(
            listOf(
                row(
                    roles = listOf("Senior:in", "Steuerleute"),
                    competitions = listOf("11 CF1x", "16 CF4x+"),
                    open = listOf("Aktivenpass", "Waage 55 kg"),
                )
            )
        )[1]

        assertEquals("Ruderklub Flensburg e.V.", data[0])
        assertEquals("Beckert", data[1])
        assertEquals("Rosa", data[2])
        assertEquals("1999", data[3])
        assertEquals("Senior:in, Steuerleute", data[4])
        assertEquals("rosa@example.org", data[5])
        assertEquals("11 CF1x, 16 CF4x+", data[6])
        assertEquals("Aktivenpass, Waage 55 kg", data[7])
    }

    @Test
    fun aMissingEmailLeavesTheCellEmptyInsteadOfNull() {
        // Nur 18 der 189 Gemeldeten der Coastal-Regatta 2026 haben eine Adresse hinterlegt -
        // die leere Zelle ist hier der Normalfall, nicht die Ausnahme.
        val data = read(listOf(row(email = null, year = null)))[1]

        assertEquals("", data[5])
        assertEquals("", data[3])
    }

    @Test
    fun rowsAreSortedByClubThenName() {
        // Angeschrieben wird vereinsweise, deshalb muss alles zu einem Verein beieinanderstehen.
        val data = read(
            listOf(
                row(club = "Ruderklub Flensburg e.V.", lastname = "Beckert"),
                row(club = "Erster Kieler Ruder-Club von 1862 e.V.", lastname = "Trog"),
                row(club = "Ruderklub Flensburg e.V.", lastname = "Andresen"),
                row(club = "Erster Kieler Ruder-Club von 1862 e.V.", lastname = "Ahlmann"),
            )
        ).drop(1)

        assertEquals(
            listOf(
                "Erster Kieler Ruder-Club von 1862 e.V." to "Ahlmann",
                "Erster Kieler Ruder-Club von 1862 e.V." to "Trog",
                "Ruderklub Flensburg e.V." to "Andresen",
                "Ruderklub Flensburg e.V." to "Beckert",
            ),
            data.map { it[0] to it[1] },
        )
    }

    @Test
    fun anEmptyListStillProducesAReadableFile() {
        val data = read(emptyList())

        assertEquals(1, data.size, "Nur die Kopfzeile")
        assertTrue(data.first().first().isNotBlank())
    }
}
