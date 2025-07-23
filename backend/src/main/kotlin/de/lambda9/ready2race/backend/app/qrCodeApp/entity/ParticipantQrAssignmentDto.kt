package de.lambda9.ready2race.backend.app.qrCodeApp.entity

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ParticipantQrAssignmentDto(
    val participantId: String,
    val firstname: String,
    val lastname: String,
    val qrCodeValue: String?,
    val namedParticipant: String,
    val competitionRegistration: String,
    val competitionName: String
)

@Serializable
data class GroupedParticipantQrAssignmentDto(
    val competitionRegistration: String,
    val competitionName: String,
    val participants: List<ParticipantQrAssignmentDto>
)