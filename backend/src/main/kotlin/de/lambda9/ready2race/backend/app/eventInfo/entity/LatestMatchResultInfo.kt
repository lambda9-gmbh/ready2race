package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class LatestMatchResultInfo(
    val matchId: UUID,
    val competitionId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val matchNumber: Int?,
    val updatedAt: LocalDateTime,
    val startTime: LocalDateTime?,
    /** Tatsächlicher Start aus `competition_match.started_at`, falls gestempelt. */
    val startedAt: LocalDateTime?,
    val teams: List<MatchResultTeamInfo>
)

data class MatchResultTeamInfo(
    val teamId: UUID,
    val teamName: String?,
    val teamNumber: Int?,
    val clubName: String?,
    /**
     * Die Vereine, die die Athleten dieses Bootes tragen, als Kette in Bootsreihenfolge - kurz
     * und lang, damit die Anzeige nach Platz entscheiden kann. Fehlt jede Angabe zur Crew, bleiben
     * beide leer und der meldende [clubName] tritt an ihre Stelle (siehe eventInfo/control).
     */
    val clubsShort: String?,
    val clubsFull: String?,
    val startNumber: Int,
    val place: Int?,
    val timeString: String?,
    val failed: Boolean,
    val failedReason: String?,
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    val deregistered: Boolean,
    val deregisteredReason: String?,
    val participants: List<ParticipantInfo>
)