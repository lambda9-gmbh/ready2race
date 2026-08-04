package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.eventSchedule.entity.ImportRowStatus
import java.util.UUID

data class ImportCandidate(
    val setupMatchId: UUID,
    val competitionTexts: Set<String>,   // identifier, shortName, name — lowercased/trimmed
    val matchName: String?,              // Setup-Zeilen-Name
    val roundName: String,
)

object ScheduleImport {

    /** Reines Matching einer Zeile; Duplikate markiert der Aufrufer über alle Zeilen hinweg. */
    fun matchRow(
        competition: String?,
        lauf: String,
        candidates: List<ImportCandidate>,
    ): Pair<ImportRowStatus, UUID?> {
        val comp = competition?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: return ImportRowStatus.FREE to null
        val laufNorm = lauf.trim().lowercase()

        val hits = candidates.filter { c ->
            comp in c.competitionTexts && c.matchName?.trim()?.lowercase() == laufNorm
        }
        return when {
            hits.size == 1 -> ImportRowStatus.LINKED to hits.single().setupMatchId
            hits.isEmpty() -> ImportRowStatus.FREE to null
            else -> ImportRowStatus.AMBIGUOUS to null
        }
    }
}
