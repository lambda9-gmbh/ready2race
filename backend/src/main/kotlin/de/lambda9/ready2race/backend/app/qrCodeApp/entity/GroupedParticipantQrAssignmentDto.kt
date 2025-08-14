package de.lambda9.ready2race.backend.app.qrCodeApp.entity

import java.util.UUID

data class GroupedParticipantQrAssignmentDto(
    val competitionRegistrationId: UUID,
    val competitionRegistrationName: String?,
    val competitionName: String,
    val participants: List<ParticipantQrAssignmentDto>
)