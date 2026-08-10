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
    /** Wann der Lauf an den Start gerufen wurde - null, solange ihn niemand aktiviert hat. */
    val activatedAt: LocalDateTime?,
    val startTime: LocalDateTime?
)