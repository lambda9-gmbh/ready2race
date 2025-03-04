package de.lambda9.ready2race.backend.app.competitionProperties.entity

import java.util.UUID

data class NamedParticipantForCompetitionDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int,
)

