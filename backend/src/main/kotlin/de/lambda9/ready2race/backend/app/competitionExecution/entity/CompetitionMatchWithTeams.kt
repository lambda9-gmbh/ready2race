package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.time.LocalDateTime
import java.util.UUID

data class CompetitionMatchWithTeams(
    val competitionSetupMatch: UUID,
    val startTime: LocalDateTime?,
    val currentlyRunning: Boolean,
    val teams: List<CompetitionMatchTeamWithRegistration>
)