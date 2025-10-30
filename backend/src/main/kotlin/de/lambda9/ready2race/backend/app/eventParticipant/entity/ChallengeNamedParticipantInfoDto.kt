package de.lambda9.ready2race.backend.app.eventParticipant.entity

data class ChallengeNamedParticipantInfoDto(
    val name: String,
    val participants: List<ChallengeParticipantInfoDto>,
)
