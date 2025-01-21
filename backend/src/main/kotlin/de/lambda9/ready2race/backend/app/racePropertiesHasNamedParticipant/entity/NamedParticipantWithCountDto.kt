package de.lambda9.ready2race.backend.app.racePropertiesHasNamedParticipant.entity

data class NamedParticipantWithCountDto(
    val name: String,
    val description: String?,
    val required: Boolean,
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int,
)