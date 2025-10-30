package de.lambda9.ready2race.backend.app.eventParticipant.entity

import java.util.UUID

data class ChallengeResultInfoDto(
    val result: Int,
    val proofDocumentId: UUID?,
)
