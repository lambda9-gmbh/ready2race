package de.lambda9.ready2race.backend.app.participantRequirement.entity

import java.util.*

data class CompetitionRegistrationNamedParticipantRequirementDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val optional: Boolean,
    val checkInApp: Boolean,
    val qrCodeRequired: Boolean
)