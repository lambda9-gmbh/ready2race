package de.lambda9.ready2race.backend.app.raceProperties.entity

data class NamedParticipantForRaceDto(
    val name: String,
    val description: String?,
    val required: Boolean,
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int,
)

