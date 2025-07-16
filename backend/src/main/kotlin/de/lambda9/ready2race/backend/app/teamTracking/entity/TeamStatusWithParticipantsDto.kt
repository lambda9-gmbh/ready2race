package de.lambda9.ready2race.backend.app.teamTracking.entity

data class TeamStatusWithParticipantsDto(
    val id: String,
    val competitionRegistrationId: String,
    val eventRegistrationId: String,
    val competitionId: String,
    val clubName: String,
    val teamName: String,
    val participants: List<TeamParticipantDto>,
    val currentStatus: ScanType?,
    val lastScanAt: String?,
    val scannedBy: String?
)