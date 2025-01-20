package de.lambda9.ready2race.backend.app.participantCount.entity

data class ParticipantCountDto(
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int,
)