package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.eventSchedule.entity.ImportRowStatus
import java.util.UUID

data class ImportCandidate(
    val setupMatchId: UUID,
    val competitionTexts: Set<String>,   // identifier, shortName, name — lowercased/trimmed
    val matchName: String?,              // Setup-Zeilen-Name
    val roundName: String,
)

/**
 * Ergebnis des Matchings einer Zeile. [availableMatches] ist nur bei
 * [ImportRowStatus.MATCH_NOT_FOUND] gefüllt: die Läufe, die es im gefundenen Wettkampf
 * tatsächlich gibt. Genau die braucht man, um den Tippfehler in der Datei zu finden.
 */
data class ImportMatchResult(
    val status: ImportRowStatus,
    val setupMatchId: UUID?,
    val availableMatches: List<String> = emptyList(),
)

object ScheduleImport {

    /**
     * Reines Matching einer Zeile; Duplikate markiert der Aufrufer über alle Zeilen hinweg.
     *
     * Das Matching läuft in zwei Stufen - erst der Wettkampf, dann der Lauf darin -, damit eine
     * Zeile ohne Treffer sagen kann, woran es lag. Vorher fielen "Wettkampf gibt es nicht" und
     * "Lauf heißt anders" beide auf FREE, und die Vorschau zeigte für beide nur "Freier Slot".
     */
    fun matchRow(
        competition: String?,
        lauf: String,
        candidates: List<ImportCandidate>,
    ): ImportMatchResult {
        val comp = competition?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: return ImportMatchResult(ImportRowStatus.FREE, null)
        val laufNorm = lauf.trim().lowercase()

        val inCompetition = candidates.filter { comp in it.competitionTexts }
        if (inCompetition.isEmpty()) {
            return ImportMatchResult(ImportRowStatus.COMPETITION_NOT_FOUND, null)
        }

        val hits = inCompetition.filter { it.matchName?.trim()?.lowercase() == laufNorm }
        return when {
            hits.size == 1 -> ImportMatchResult(ImportRowStatus.LINKED, hits.single().setupMatchId)
            hits.isEmpty() -> ImportMatchResult(
                ImportRowStatus.MATCH_NOT_FOUND,
                null,
                // Reihenfolge der Setup-Zeilen beibehalten, Duplikate über mehrere Runden
                // hinweg (etwa zwei Runden mit "Finale A") nur einmal nennen.
                inCompetition.mapNotNull { it.matchName?.trim() }.filter { it.isNotEmpty() }.distinct(),
            )

            else -> ImportMatchResult(ImportRowStatus.AMBIGUOUS, null)
        }
    }
}
