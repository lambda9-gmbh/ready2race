package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.time.LocalDateTime
import java.util.UUID

data class MatchForRunningStatusDto(
    val id: UUID,
    val competitionId: UUID,
    val competitionName: String,
    val roundNumber: Int,
    val roundName: String,
    val matchNumber: Int,
    val matchName: String?,
    val hasPlacesSet: Boolean,
    val currentlyRunning: Boolean,
    val startTime: LocalDateTime?
)