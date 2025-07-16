package de.lambda9.ready2race.backend.app.teamTracking.entity

data class TeamStatusDto(
    val competitionRegistrationId: String,
    val teamName: String,
    val currentStatus: ScanType?,
    val lastScanAt: String?,
    val scannedBy: String?
)