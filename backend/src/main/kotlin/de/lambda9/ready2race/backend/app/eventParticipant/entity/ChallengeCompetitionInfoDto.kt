package de.lambda9.ready2race.backend.app.eventParticipant.entity

import java.time.LocalDateTime
import java.util.UUID

data class ChallengeCompetitionInfoDto(
    val id: UUID,
    val name: String,
    val identifier: String,
    val resultInfo: ChallengeResultInfoDto?,
    val proofRequired: Boolean,
    val teamInfo: ChallengeTeamInfoDto,
    val challengeStart: LocalDateTime,
    val challengeEnd: LocalDateTime,
)
