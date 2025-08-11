package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.util.UUID

data class UpcomingMatchTeamInfo(
    val teamId: UUID,
    val teamName: String?,
    val startNumber: Int?,
    val clubName: String?,
    val participants: List<UpcomingMatchParticipantInfo>
)