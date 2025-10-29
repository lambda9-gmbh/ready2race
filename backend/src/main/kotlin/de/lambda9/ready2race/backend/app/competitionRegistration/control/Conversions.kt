package de.lambda9.ready2race.backend.app.competitionRegistration.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import de.lambda9.ready2race.backend.app.competitionDeregistration.entity.CompetitionDeregistrationDto
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationTeamDto
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationTeamNamedParticipantDto
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.ParticipantForCompetitionRegistrationTeam
import de.lambda9.ready2race.backend.app.participantRequirement.control.toNamedParticipantRequirementDto
import de.lambda9.ready2race.backend.app.participantRequirement.control.toRequirementDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.CheckedParticipantRequirement
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantScanType
import de.lambda9.ready2race.backend.app.ratingcategory.control.toDto
import de.lambda9.ready2race.backend.app.substitution.entity.ParticipantForExecutionDto
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationTeamParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementForEventRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.get
import kotlin.text.get

fun CompetitionRegistrationTeamRecord.toDto(
    requirementsForEvent: List<ParticipantRequirementForEventRecord>,
    namedParticipants: Map<UUID, List<ParticipantForCompetitionRegistrationTeam>>,
    challengeResultValue: Int?,
    challengeResultDocuments: Map<UUID, String>?
): App<Nothing, CompetitionRegistrationTeamDto> =
    (ratingCategory?.toDto() ?: KIO.ok(null)).map { cat ->
        CompetitionRegistrationTeamDto(
            id = competitionRegistrationId!!,
            name = teamName,
            clubId = clubId!!,
            clubName = clubName!!,
            namedParticipants = participants!!.filterNotNull().groupBy { it.roleId!! }.map { np ->
                CompetitionRegistrationTeamNamedParticipantDto(
                    namedParticipantId = np.key,
                    namedParticipantName = np.value[0].role!!,
                    participants = namedParticipants[np.key] ?: listOf(),
                    participantRequirements = requirementsForEvent
                        .filter { eventReq -> eventReq.requirements!!.any { npReq -> np.key == npReq!!.id } }
                        .map { it.toNamedParticipantRequirementDto(np.key) }
                )
            },
            deregistration = deregistration?.let {
                CompetitionDeregistrationDto(
                    competitionSetupRoundId = it.competitionSetupRound,
                    reason = it.reason
                )
            },
            globalParticipantRequirements = requirementsForEvent.filter { it.requirements?.size == 0 }
                .map { it.toRequirementDto() },
            challengeResultValue = challengeResultValue,
            challengeResultDocuments = challengeResultDocuments,
            ratingCategory = cat,
        )
    }

fun CompetitionRegistrationTeamParticipantRecord.toParticipantForExecutionDto(
    clubId: UUID,
    clubName: String,
    registrationName: String?,
) = KIO.ok(
    ParticipantForExecutionDto(
        id = participantId!!,
        namedParticipantId = roleId!!,
        namedParticipantName = role!!,
        firstName = firstname!!,
        lastName = lastname!!,
        year = year!!,
        gender = gender!!,
        clubId = clubId,
        clubName = clubName,
        competitionRegistrationId = competitionRegistrationId!!,
        competitionRegistrationName = registrationName,
        external = external,
        externalClubName = externalClubName
    )
)

fun ParticipantForExecutionDto.toParticipantForCompetitionRegistrationTeam(
    qrCodeId: String?,
    participantRequirementsChecked: List<CheckedParticipantRequirement>,
    currentStatus: ParticipantScanType?,
    lastScanAt: LocalDateTime?,
    lastScanBy: AppUserNameDto?
): App<Nothing, ParticipantForCompetitionRegistrationTeam> = KIO.ok(
    ParticipantForCompetitionRegistrationTeam(
        id = id,
        firstname = firstName,
        lastname = lastName,
        year = year,
        gender = gender,
        external = external ?: false,
        externalClubName = externalClubName,
        qrCodeId = qrCodeId,
        participantRequirementsChecked = participantRequirementsChecked,
        currentStatus = currentStatus,
        lastScanAt = lastScanAt,
        lastScanBy = lastScanBy
    )
)