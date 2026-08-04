package de.lambda9.ready2race.backend.app.liveDashboard.entity

import java.time.LocalDateTime
import java.util.UUID

enum class LiveDashboardMatchState { RUNNING, FINISHED, UPCOMING, UNSCHEDULED }

enum class LiveDashboardInvoiceState { PAID, OPEN, NONE }

enum class TimeCheckStatus { OK, TOO_EARLY, LATE, NOT_CHECKED }

data class TimeCheckDto(
    val deltaMinutes: Long?,
    val status: TimeCheckStatus,
)

data class LiveDashboardRequirementStatusDto(
    val requirementId: UUID,
    val name: String,
    val description: String?,
    val optional: Boolean,
    val checked: Boolean,
    val checkedAt: LocalDateTime?,
    val note: String?,
    val timeCheck: TimeCheckDto?,
)

data class LiveDashboardParticipantDto(
    val participantId: UUID,
    val firstName: String,
    val lastName: String,
    val namedRole: String?,
    val year: Int?,
    val gender: String?,
    val externalClubName: String?,
    /** Name of the participant this one replaced, if they were substituted into this round. */
    val substitutedFor: String?,
    val substitutionReason: String?,
    val requirements: List<LiveDashboardRequirementStatusDto>,
)

/**
 * Verdichtung der Teilnahmebedingungen einer Mannschaft. Die Liste zeigt daraus ein Ampel-Icon;
 * die Bedingungen selbst holt erst der Detail-Dialog. Bei 150 Personen mal drei Bedingungen ist
 * das der Unterschied zwischen einer Antwort von 100 KB und einer von wenigen KB.
 */
data class LiveDashboardRequirementSummaryDto(
    val total: Int,
    val fulfilled: Int,
    val missingRequired: Int,
    val missingOptional: Int,
    /** Prüfungen außerhalb des konfigurierten Zeitfensters. */
    val timeIssues: Int,
)

data class LiveDashboardTeamDto(
    val teamId: UUID,
    val teamName: String?,
    val clubName: String?,
    val actualClubName: String?,
    val startNumber: Int?,
    val place: Int?,
    val time: String?,
    val failed: Boolean,
    val failedReason: String?,
    /** Zeitstrafe in Sekunden; die Ergebniszeit enthält sie bereits. */
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    val deregistered: Boolean,
    val deregisteredReason: String?,
    val invoiceState: LiveDashboardInvoiceState,
    val requirements: LiveDashboardRequirementSummaryDto,
    /** Ob mindestens eine Person für diese Runde umgemeldet wurde. */
    val substituted: Boolean,
)

/** Was der Detail-Dialog zusätzlich braucht; wird einzeln je Mannschaft geladen. */
data class LiveDashboardTeamDetailDto(
    val teamId: UUID,
    val participants: List<LiveDashboardParticipantDto>,
)

data class LiveDashboardMatchDto(
    val matchId: UUID,
    val state: LiveDashboardMatchState,
    val competitionId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val executionOrder: Int,
    val startTime: LocalDateTime?,
    val startedAt: LocalDateTime?,
    val currentlyRunning: Boolean,
    val elapsedMinutes: Long?,
    val teams: List<LiveDashboardTeamDto>,
)

/**
 * Ein Platzhalter im Zeitstrahl des Live-Dashboards - entweder ein wartender Lauf-Slot (Runde noch
 * nicht erzeugt) oder ein FREE-Slot/Programmpunkt (z.B. "Mittagspause"). Bewusst ohne Team-/
 * Personendaten, die gibt es für beide Platzhalter-Arten ohnehin nicht. [name] unterscheidet die
 * Fälle: gesetzt für Programmpunkte, null für Lauf-Platzhalter.
 */
data class PendingSlotDto(
    val slotId: UUID,
    val startTime: LocalDateTime,
    /** Name des Programmpunkts - null bei einem Lauf-Platzhalter. */
    val name: String?,
    val competitionName: String?,
    val roundName: String?,
    val matchName: String?,
)

data class LiveDashboardDto(
    val matches: List<LiveDashboardMatchDto>,
    /** Aufsteigend nach Startzeit; in beiden Scopes (ALL und LIVE) enthalten - die Liste ist klein. */
    val pendingSlots: List<PendingSlotDto>,
)
