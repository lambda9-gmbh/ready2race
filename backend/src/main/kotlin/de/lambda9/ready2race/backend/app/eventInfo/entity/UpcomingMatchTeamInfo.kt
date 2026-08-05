package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.util.UUID

data class UpcomingMatchTeamInfo(
    val teamId: UUID,
    val teamName: String?,
    /** Die n-te Mannschaft dieses Vereins in diesem Wettkampf, aus `competition_registration`. */
    val teamNumber: Int?,
    val startNumber: Int?,
    val clubName: String?,
    val actualClubName: String?,
    val participants: List<UpcomingMatchParticipantInfo>
)