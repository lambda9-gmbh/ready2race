package de.lambda9.ready2race.backend.app.eventParticipant.entity

import de.lambda9.ready2race.backend.app.event.entity.MatchResultType
import java.util.UUID

data class ChallengeInfoDto(
    val eventId: UUID,
    val eventName: String,
    val resultType: MatchResultType,
    val competitions: List<ChallengeCompetitionInfoDto>,
)
