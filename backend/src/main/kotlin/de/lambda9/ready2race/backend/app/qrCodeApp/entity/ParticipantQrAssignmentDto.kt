package de.lambda9.ready2race.backend.app.qrCodeApp.entity

import java.util.UUID


data class ParticipantQrAssignmentDto(
    val participantId: UUID,
    val firstname: String,
    val lastname: String,
    val qrCodeValue: String?,
    val namedParticipantName: String,
)