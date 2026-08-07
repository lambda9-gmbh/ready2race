package de.lambda9.ready2race.backend.app.eventSchedule.entity

import java.time.LocalDateTime
import java.util.UUID

/**
 * Warum eine Zeile so eingeordnet wurde. FREE, COMPETITION_NOT_FOUND und MATCH_NOT_FOUND landen
 * alle als freier Slot im Zeitstrahl - nur FREE ist dabei gewollt, die beiden anderen sind
 * vermutlich Fehler in der Datei und werden in der Vorschau entsprechend benannt.
 */
enum class ImportRowStatus { LINKED, FREE, COMPETITION_NOT_FOUND, MATCH_NOT_FOUND, AMBIGUOUS, DUPLICATE }

/** Ergebnis einer einzelnen Import-Zeile nach dem Matching, vor der DTO-Aufbereitung. */
data class ImportRowResult(
    val rowNumber: Int,
    val startTime: LocalDateTime,
    val competitionText: String?,
    val laufText: String,
    val status: ImportRowStatus,
    val setupMatchId: UUID?,
    val availableMatches: List<String> = emptyList(),
)

/**
 * Zeile für die Preview-UI. `targetLabel` ist nur für LINKED-Zeilen gesetzt
 * ("competitionName – roundName – matchName"), sonst null. `availableMatches` ist nur für
 * MATCH_NOT_FOUND gefüllt und nennt die Läufe, die es im gefundenen Wettkampf gibt.
 */
data class ImportRowResultDto(
    val rowNumber: Int,
    val startTime: LocalDateTime,
    val competitionText: String?,
    val laufText: String,
    val status: ImportRowStatus,
    val targetLabel: String?,
    val availableMatches: List<String>,
)

data class ScheduleImportResultDto(
    val rows: List<ImportRowResultDto>,
    val applied: Boolean,
)
