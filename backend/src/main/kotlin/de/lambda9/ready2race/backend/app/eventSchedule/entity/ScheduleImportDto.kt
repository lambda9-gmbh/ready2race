package de.lambda9.ready2race.backend.app.eventSchedule.entity

import java.time.LocalDateTime
import java.util.UUID

enum class ImportRowStatus { LINKED, FREE, AMBIGUOUS, DUPLICATE }

/** Ergebnis einer einzelnen Import-Zeile nach dem Matching, vor der DTO-Aufbereitung. */
data class ImportRowResult(
    val rowNumber: Int,
    val startTime: LocalDateTime,
    val competitionText: String?,
    val laufText: String,
    val status: ImportRowStatus,
    val setupMatchId: UUID?,
)

/**
 * Zeile für die Preview-UI. `targetLabel` ist nur für LINKED-Zeilen gesetzt
 * ("competitionName – roundName – matchName"), sonst null.
 */
data class ImportRowResultDto(
    val rowNumber: Int,
    val startTime: LocalDateTime,
    val competitionText: String?,
    val laufText: String,
    val status: ImportRowStatus,
    val targetLabel: String?,
)

data class ScheduleImportResultDto(
    val rows: List<ImportRowResultDto>,
    val applied: Boolean,
)
