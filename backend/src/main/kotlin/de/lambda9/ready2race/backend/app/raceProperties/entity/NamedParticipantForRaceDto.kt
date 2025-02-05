package de.lambda9.ready2race.backend.app.raceProperties.entity

import java.util.UUID

data class NamedParticipantForRaceDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val required: Boolean,
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int,
)

