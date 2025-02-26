package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.math.BigDecimal
import java.util.*

data class EventRegistrationRaceDto(
    val id: UUID,
    val identifier: String,
    val name: String,
    val shortName: String?,
    val description: String?,
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int,
    val participationFee: BigDecimal?,
    val rentalFee: BigDecimal?,
    val raceCategory: String?,
    val namedParticipant: List<EventRegistrationNamedParticipantDto>?,
    val days: List<UUID>
)