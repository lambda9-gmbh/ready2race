package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.util.*

data class CompetitionRegistrationLockedDto(
    val competitionId: UUID,
    val teams: List<CompetitionRegistrationTeamLockedDto>,
)
