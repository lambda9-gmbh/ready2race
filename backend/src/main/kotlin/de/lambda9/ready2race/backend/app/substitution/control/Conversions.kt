package de.lambda9.ready2race.backend.app.substitution.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.substitution.entity.ParticipantForExecutionDto
import de.lambda9.ready2race.backend.app.substitution.entity.PossibleSubstitutionParticipantDto
import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.UUID

fun SubstitutionRequest.toRecord(
    userId: UUID,
    orderForRound: Long,
    namedParticipant: UUID
): App<Nothing, SubstitutionRecord> = KIO.ok(
    LocalDateTime.now().let { now ->
        SubstitutionRecord(
            id = UUID.randomUUID(),
            competitionRegistration = competitionRegistrationId,
            competitionSetupRound = competitionSetupRound,
            participantOut = participantOut,
            participantIn = participantIn,
            reason = reason,
            namedParticipant = namedParticipant,
            orderForRound = orderForRound,
            createdAt = now,
            createdBy = userId,
            updatedAt = now,
            updatedBy = userId,
        )
    }
)

fun SubstitutionViewRecord.toParticipantParticipatingInRoundDto(
    participant: ParticipantRecord,
) = KIO.ok(
    ParticipantForExecutionDto(
        id = participant.id,
        namedParticipantId = namedParticipantId!!,
        namedParticipantName = namedParticipantName!!,
        firstName = participant.firstname,
        lastName = participant.lastname,
        clubId = clubId!!,
        clubName = clubName!!,
        competitionRegistrationId = competitionRegistrationId!!,
        competitionRegistrationName = competitionRegistrationName,
        external = participant.external,
        externalClubName = participant.externalClubName
    )
)

fun RegisteredCompetitionTeamParticipantRecord.toParticipantParticipatingInRoundDto(
    team: CompetitionMatchTeamWithRegistrationRecord
) = KIO.ok(
    ParticipantForExecutionDto(
        id = participantId!!,
        namedParticipantId = roleId!!,
        namedParticipantName = role!!,
        firstName = firstname!!,
        lastName = lastname!!,
        clubId = team.clubId!!,
        clubName = team.clubName!!,
        competitionRegistrationId = team.competitionRegistration!!,
        competitionRegistrationName = team.registrationName,
        external = external,
        externalClubName = externalClubName
    )
)

fun ParticipantRecord.toPossibleSubstitutionParticipantDto() = KIO.ok(
    PossibleSubstitutionParticipantDto(
        id = id,
        firstName = firstname,
        lastName = lastname,
        external = external,
        externalClubName = externalClubName
    )
)

fun ParticipantForExecutionDto.toPossibleSubstitutionParticipantDto() = KIO.ok(
    PossibleSubstitutionParticipantDto(
        id = id,
        firstName = firstName,
        lastName = lastName,
        external = external,
        externalClubName = externalClubName
    )
)