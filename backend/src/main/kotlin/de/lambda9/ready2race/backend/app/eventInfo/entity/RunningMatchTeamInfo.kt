package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.util.UUID

data class RunningMatchTeamInfo(
    val teamId: UUID,
    val teamName: String?,
    val startNumber: Int?,
    val clubName: String?,
    val currentScore: Int?,
    val currentPosition: Int?,
    val participants: List<UpcomingMatchParticipantInfo>
)