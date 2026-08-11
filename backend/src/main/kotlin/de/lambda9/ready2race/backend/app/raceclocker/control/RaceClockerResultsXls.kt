package de.lambda9.ready2race.backend.app.raceclocker.control

import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.util.UUID

/**
 * Liest das „Results"-Blatt eines RaceClocker-xlsx-Exports — der Notfallweg, wenn am Steg das Netz
 * fehlt und der Live-Abruf nicht durchkommt.
 *
 * Warum ein eigener Parser statt des spaltenkonfigurierten [de.lambda9.ready2race.backend.xls.XLS]:
 * Das Blatt ist ein Bericht, keine saubere Tabelle. Es trägt die Kopfzeile „Rank" mehrfach (einmal je
 * Zwischenzeit-Marke), und zwischen den Booten stehen Wellen-Titelzeilen („08:00 Vorlauf 1 DM") mit
 * Text nur in der ersten Spalte. Beides bringt den generischen, namensbasierten Import durcheinander.
 * Gebraucht werden ohnehin nur drei eindeutige Spalten: „Name" (leer ⇒ Titelzeile, überspringen),
 * „Result" (Zeit oder DNS/DNF/DQ) und „Extra info".
 *
 * „Extra info" trägt die R2R-Kennung im Format `<WellenID>: <matchTeamId>;` — dieselben UUIDs wie im
 * Live-Feed (siehe [RaceClockerFeed.extractIds]). Der Parser sammelt hier ALLE UUIDs der Zelle; welche
 * davon die gesuchte Mannschaft ist, entscheidet der Aufrufer über die bekannten Ids des Laufs — genau
 * wie beim Feed. Der Platz wird bewusst NICHT aus der (mehrdeutigen, geklammerten) Rank-Spalte gelesen:
 * ready2race rechnet ihn aus den Zeiten, dieselbe Regel wie überall.
 *
 * Das Ergebnis kommt als [RaceClockerFeedRow] zurück, damit die bestehende Zuordnungs- und
 * Schreiblogik (`assignFeedRows`/`applyRaceClockerRows`-Nachbarschaft) unverändert weiterträgt —
 * ohne Startzeit und ohne Zwischenzeiten, die trägt nur der Live-Feed.
 */
object RaceClockerResultsXls {

    private val uuidPattern =
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    sealed interface ParseResult {
        data class Ok(val rows: List<RaceClockerFeedRow>) : ParseResult
        data object Invalid : ParseResult
    }

    fun parse(bytes: ByteArray): ParseResult {
        val workbook = try {
            WorkbookFactory.create(bytes.inputStream() as InputStream)
        } catch (_: Exception) {
            return ParseResult.Invalid
        }

        workbook.use { wb ->
            val sheet = (0 until wb.numberOfSheets)
                .map { wb.getSheetAt(it) }
                .firstOrNull { it.sheetName.equals("Results", ignoreCase = true) }
                ?: wb.getSheetAt(0)
                ?: return ParseResult.Invalid

            val header = sheet.firstOrNull() ?: return ParseResult.Invalid
            // Eindeutige Spalten; bei doppelten Überschriften ("Rank") gewinnt die letzte, aber die
            // brauchen wir nicht.
            val cols = header.mapIndexedNotNull { idx, cell ->
                (cell?.stringValueOrNull())?.let { it.trim() to idx }
            }.toMap()

            val nameCol = cols["Name"] ?: return ParseResult.Invalid
            val resultCol = cols["Result"] ?: return ParseResult.Invalid
            val extraCol = cols.entries.firstOrNull { it.key.equals("Extra info", ignoreCase = true) }?.value
                ?: return ParseResult.Invalid

            val rows = sheet.drop(1).mapNotNull { row -> row.toFeedRow(nameCol, resultCol, extraCol) }
            return ParseResult.Ok(rows)
        }
    }

    private fun Row.toFeedRow(nameCol: Int, resultCol: Int, extraCol: Int): RaceClockerFeedRow? {
        val name = getCell(nameCol)?.stringValueOrNull()?.trim()
        // Leerer Name = Wellen-Titelzeile (nur Spalte 0 gefüllt) → keine Mannschaft.
        if (name.isNullOrBlank()) return null

        val ids = getCell(extraCol)?.stringValueOrNull()
            ?.let { uuidPattern.findAll(it).mapNotNull { m -> runCatching { UUID.fromString(m.value) }.getOrNull() }.toList() }
            ?: emptyList()
        if (ids.isEmpty()) return null

        val result = getCell(resultCol).resultString()

        return RaceClockerFeedRow(
            name = name,
            rank = null,
            bib = null,
            wave = null,
            ids = ids,
            result = result,
            start = null,
            penaltySeconds = null,
            penaltyNote = null,
        )
    }

    /**
     * „Result" ist entweder eine als Uhrzeit formatierte Zahl (Bruchteil eines Tages) oder ein Text
     * (`DNF`/`DNS`/`DQ`). Die Zahl wird in `H:MM:SS.d` überführt — das Format, das
     * [de.lambda9.ready2race.backend.parsing.Parser.timecode] erwartet und das der Live-Feed
     * ebenfalls liefert.
     */
    private fun Cell?.resultString(): String? = when {
        this == null -> null
        cellType == CellType.STRING -> stringCellValue?.trim()?.takeIf { it.isNotBlank() }
        cellType == CellType.NUMERIC -> formatDayFraction(numericCellValue)
        cellType == CellType.FORMULA -> when (cachedFormulaResultType) {
            CellType.STRING -> stringCellValue?.trim()?.takeIf { it.isNotBlank() }
            CellType.NUMERIC -> formatDayFraction(numericCellValue)
            else -> null
        }
        else -> null
    }

    /** Tagesbruchteil → `H:MM:SS.d` (Zehntel, wie RaceClocker misst). */
    private fun formatDayFraction(fraction: Double): String? {
        if (fraction <= 0.0) return null
        val totalTenths = Math.round(fraction * 24 * 60 * 60 * 10)
        val tenths = (totalTenths % 10).toInt()
        val totalSeconds = totalTenths / 10
        val seconds = (totalSeconds % 60).toInt()
        val minutes = ((totalSeconds / 60) % 60).toInt()
        val hours = (totalSeconds / 3600).toInt()
        return "%d:%02d:%02d.%d".format(hours, minutes, seconds, tenths)
    }

    private fun Cell.stringValueOrNull(): String? =
        if (cellType == CellType.STRING) stringCellValue else null
}
