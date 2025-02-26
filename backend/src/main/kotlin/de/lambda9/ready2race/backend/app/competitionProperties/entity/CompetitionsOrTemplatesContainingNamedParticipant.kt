package de.lambda9.ready2race.backend.app.competitionProperties.entity

data class CompetitionsOrTemplatesContainingNamedParticipant(
    val templates: List<CompetitionPropertiesContainingNamedParticipant>?,
    val competitions: List<CompetitionPropertiesContainingNamedParticipant>?,
)