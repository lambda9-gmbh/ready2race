package de.lambda9.ready2race.backend.app.raceProperties.entity

data class RacesOrTemplatesContainingNamedParticipant(
    val templates: List<RacePropertiesContainingNamedParticipant>?,
    val races: List<RacePropertiesContainingNamedParticipant>?,
)