package de.lambda9.ready2race.backend.app.racePropertiesHasNamedParticipant.entity

data class RacePropertiesHasNamedParticipantDto (
    val namedParticipant : String,
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int,
)