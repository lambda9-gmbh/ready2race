package de.lambda9.ready2race.backend.app.eventParticipant.entity

import java.util.UUID

data class ChallengeTeamInfoDto(
    val id: UUID,
    val name: String?,
    val namedParticipants: List<ChallengeNamedParticipantInfoDto>,
)
