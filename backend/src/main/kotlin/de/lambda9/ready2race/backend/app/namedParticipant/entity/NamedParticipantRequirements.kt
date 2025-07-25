package de.lambda9.ready2race.backend.app.namedParticipant.entity

data class NamedParticipantRequirements(
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int
)