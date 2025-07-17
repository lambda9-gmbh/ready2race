package de.lambda9.ready2race.backend.app.substitution.entity

import java.util.UUID

data class SubstitutionDto(
    val id: UUID,
    val reason: String?,
    val orderForRound: Long,
    val setupRoundId: UUID,
    val setupRoundName: String,
    val competitionRegistrationId: UUID,
    val competitionRegistrationName: String?,
    val clubId: UUID,
    val clubName: String,
    val namedParticipantId: UUID,
    val namedParticipantName: String,
    val participantOut: SubstitutionParticipantDto,
    val participantIn: SubstitutionParticipantDto,
    val swapSubstitution: UUID?
)