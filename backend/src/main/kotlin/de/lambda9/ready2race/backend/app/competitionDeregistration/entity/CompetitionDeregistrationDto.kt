package de.lambda9.ready2race.backend.app.competitionDeregistration.entity

import java.util.UUID

data class CompetitionDeregistrationDto(
    val competitionSetupRoundId: UUID?,
    val reason: String?,
)