package de.lambda9.ready2race.backend.app.teamTracking.entity

data class TeamTrackingScanDto(
    val competitionRegistrationId: String,
    val eventId: String,
    val scanType: ScanType,
    val success: Boolean,
    val message: String? = null,
    val teamName: String? = null,
    val currentStatus: TeamStatusDto? = null
)