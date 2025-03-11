package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.util.*

data class EventRegistrationNamedParticipantDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int
)