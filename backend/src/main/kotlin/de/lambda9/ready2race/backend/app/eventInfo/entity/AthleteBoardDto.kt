package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.time.LocalDateTime
import java.util.UUID

/**
 * Antwort der Athleten-Anzeige. Bewusst schlanker als die Info-DTOs daneben:
 * Jahrgang, Geschlecht, Teilnehmer-IDs und der externe Vereinsname fehlen,
 * weil sie nicht angezeigt werden und im Mobilfunknetz kosten.
 */
data class AthleteBoardDto(
    val eventName: String,
    val serverTime: LocalDateTime,
    val refreshIntervalSeconds: Int,
    val showCountdown: Boolean,
    val running: List<AthleteBoardMatch>,
    val upcoming: List<AthleteBoardMatch>,
    val results: List<AthleteBoardResult>,
)

data class AthleteBoardMatch(
    val matchId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val startTime: LocalDateTime?,
    val startState: AthleteBoardStartState,
    val teams: List<AthleteBoardTeam>,
)

data class AthleteBoardTeam(
    /** Startposition im Lauf, aus `competition_match_team.start_number`. */
    val lane: Int?,
    val clubName: String?,
    val teamName: String?,
    val participants: List<AthleteBoardParticipant>,
)

data class AthleteBoardParticipant(
    val name: String,
    val role: String?,
)

data class AthleteBoardResult(
    val matchId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val startTime: LocalDateTime?,
    val teams: List<AthleteBoardResultTeam>,
)

data class AthleteBoardResultTeam(
    val place: Int?,
    val lane: Int,
    val clubName: String?,
    val teamName: String?,
    val timeString: String?,
    val failed: Boolean,
    val failedReason: String?,
)
