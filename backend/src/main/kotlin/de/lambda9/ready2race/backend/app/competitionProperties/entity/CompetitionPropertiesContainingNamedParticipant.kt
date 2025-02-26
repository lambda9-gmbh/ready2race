package de.lambda9.ready2race.backend.app.competitionProperties.entity

import java.util.UUID

data class CompetitionPropertiesContainingNamedParticipant(
    val competitionTemplateId: UUID?,
    val competitionId: UUID?,
    val name: String,
    val shortName: String?,
)