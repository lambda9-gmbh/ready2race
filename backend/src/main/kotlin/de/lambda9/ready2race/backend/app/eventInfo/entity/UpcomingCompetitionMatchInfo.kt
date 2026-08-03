package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.time.LocalDateTime
import java.util.UUID

data class UpcomingCompetitionMatchInfo(
    val matchId: UUID,
    val matchNumber: Int?,
    val competitionId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val scheduledStartTime: LocalDateTime?,
    val placeName: String?,
    val roundNumber: Int?,
    val roundName: String?,
    val matchName: String?,
    val executionOrder: Int,
    val teams: List<UpcomingMatchTeamInfo>,
    /**
     * true für einen Platzhalter aus einem wartenden Zeitstrahl-Slot (Runde noch nicht
     * erzeugt) - matchId zeigt dann auf die Setup-Zeile, nicht auf einen echten Lauf, und
     * teams ist immer leer.
     */
    val pendingRound: Boolean = false,
)