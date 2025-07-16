package de.lambda9.ready2race.backend.app.teamTracking.entity

data class TeamTrackingDto(
    val id: String,
    val competitionRegistrationId: String,
    val eventId: String,
    val scanType: ScanType,
    val scannedAt: String,
    val scannedBy: String?
)

