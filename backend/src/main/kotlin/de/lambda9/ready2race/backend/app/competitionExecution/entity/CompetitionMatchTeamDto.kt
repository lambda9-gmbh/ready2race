package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.util.UUID

data class CompetitionMatchTeamDto(
    val registrationId: UUID,
    val teamNumber: Int,
    val clubId: UUID,
    val clubName: String,
    val actualClubName: String?,
    val namedParticipants: List<CompetitionTeamNamedParticipantDto>,
    val name: String?,
    val startNumber: Int,
    val place: Int?,
    val timeString: String?,
    val placesCalculated: Boolean,
    val deregistered: Boolean,
    val deregistrationReason: String?,
    val failed: Boolean,
    val failedReason: String?,
    /** Zeitstrafe in Sekunden; die Ergebniszeit enthält sie bereits. */
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    /**
     * Zwischenzeiten aus RaceClocker, in der Reihenfolge der Marken auf der Strecke. Leer, wenn
     * das Rennen keine Split-Spalten führt.
     */
    val laps: List<MatchTeamLapDto> = emptyList(),
)

/** Eine Zwischenzeit: Spaltenname aus RaceClocker und kumulierte Fahrzeit als Anzeige-Text. */
data class MatchTeamLapDto(
    val name: String,
    val timeString: String,
)