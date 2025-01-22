package de.lambda9.ready2race.backend.app.raceProperties.entity

import java.util.UUID

data class NamedParticipantForRaceRequestDto(
    val namedParticipant: UUID,
    val required: Boolean,
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int,
)
