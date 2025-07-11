package de.lambda9.ready2race.backend.app.substitution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionTeamParticipantDto
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupRoundRepo
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupError
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantError
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.app.substitution.control.toParticipantParticipatingInRoundDto
import de.lambda9.ready2race.backend.app.substitution.control.toPossibleSubstitutionParticipantDto
import de.lambda9.ready2race.backend.app.substitution.control.toRecord
import de.lambda9.ready2race.backend.app.substitution.entity.*
import de.lambda9.ready2race.backend.calls.requests.logger
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionViewRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.util.UUID

object SubstitutionService {

    fun addSubstitution(
        userId: UUID,
        request: SubstitutionRequest
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {
        // todo: check for valid request

        val maxOrderForRound = !SubstitutionRepo.getByRound(request.competitionSetupRound).orDie()
            .map { subs -> subs.maxOfOrNull { it.orderForRound } }

        val substitutions = !SubstitutionRepo.getViewByRound(request.competitionSetupRound).orDie()
        val reqPSubbedIn = substitutions
            .filter { it.participantIn!!.id == request.participantOut }

        val registrationParticipants = !getParticipantsInRound(request.competitionSetupRound)

        val namedParticipantSubbing = if (reqPSubbedIn.isNotEmpty()) {
            reqPSubbedIn
                .sortedBy { it.orderForRound }
                .last()
                .namedParticipantId!!
        } else {
            registrationParticipants
                .find { it.id == request.participantOut }
                ?.namedParticipantId
                ?: return@comprehension KIO.fail(SubstitutionError.ParticipantOutNotFound)
        }

        val record = !request.toRecord(userId, (maxOrderForRound ?: 0) + 1, namedParticipantSubbing)

        SubstitutionRepo.create(record).orDie().map { ApiResponse.Created(it) }
    }


    fun deleteSubstitution(
        id: UUID,
    ): App<SubstitutionError, ApiResponse.NoData> = KIO.comprehension {

        // todo: check if the request is valid. Only subs that have no following sub-dependencies are allowed to be deleted

        val deleted = !SubstitutionRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(SubstitutionError.NotFound)
        } else {
            noData
        }
    }

    fun getParticipantsCurrentlyParticipatingInRound(
        competitionSetupRoundId: UUID,
    ): App<CompetitionSetupError, ApiResponse.ListDto<ParticipantForExecutionDto>> = KIO.comprehension {
        val registrationParticipants = !getParticipantsInRound(competitionSetupRoundId)

        val substitutions = !SubstitutionRepo.getViewByRound(competitionSetupRoundId).orDie()

        val subbedOutRegistrationParticipants = getSubbedOutParticipants(registrationParticipants, substitutions)
        val subbedInParticipants = !getSubbedInParticipants(substitutions)

        // Get registered participants that are NOT currently subbed out + currently subbed in participants that are not registered
        val registeredNotSubbedOut = registrationParticipants.filter { regP ->
            subbedOutRegistrationParticipants.find { subbedOutP ->
                subbedOutP.id == regP.id
            } == null
        }
        val subbedInNotRegistered = subbedInParticipants.filter { subbedInP ->
            registrationParticipants.find { regP ->
                regP.id == subbedInP.id
            } == null
        }

        val availableParticipantsForSubstitution = registeredNotSubbedOut + subbedInNotRegistered

        logger.info { "getParticipantsCurrentlyParticipatingInRound: Currently Participating: Registered (not subbed out): $registeredNotSubbedOut" }
        logger.info { "getParticipantsCurrentlyParticipatingInRound: Currently Participating: Subbed in (not registered prior): $subbedInNotRegistered" }

        KIO.ok(ApiResponse.ListDto(availableParticipantsForSubstitution))
    }

    fun getPossibleSubstitutionsForParticipant(
        competitionSetupRoundId: UUID,
        participantId: UUID,
    ): App<ServiceError, ApiResponse.Dto<PossibleSubstitutionsForParticipantDto>> = KIO.comprehension {

        val participant =
            !ParticipantRepo.get(participantId).orDie().onNullFail { ParticipantError.ParticipantNotFound }

        val participantsInClub = !ParticipantRepo.getByClubId(participant.club).orDie()

        val registrationParticipants = !getParticipantsInRound(competitionSetupRoundId)

        val substitutions = !SubstitutionRepo.getViewByRound(competitionSetupRoundId).orDie()
        val subbedOutRegistrationParticipants = getSubbedOutParticipants(registrationParticipants, substitutions)
        val subbedInParticipants = !getSubbedInParticipants(substitutions)


        val clubMembersRegistered = registrationParticipants
            .filter { it.clubId == participant.club }
            .filter { it.id != participantId }
            .map { !it.toPossibleSubstitutionParticipantDto() }

        val (psRegisteredParticipating, psRegisteredNotParticipating) = clubMembersRegistered.partition { p ->
            subbedOutRegistrationParticipants.find { it.id == p.id } == null
        }


        val (psNotRegisteredSubbedIn, psNotRegisteredNotSubbedIn) = participantsInClub.filter { p ->
            clubMembersRegistered.find { regP -> regP.id == p.id } == null && p.id != participantId
        }.map { !it.toPossibleSubstitutionParticipantDto() }.partition { p ->
            subbedInParticipants.find { regP -> regP.id == p.id } != null
        }


        // Split Members into "currentlyParticipating" and "notCurrentlyParticipating"

        KIO.ok(
            ApiResponse.Dto(
                PossibleSubstitutionsForParticipantDto(
                    currentlyParticipating = psRegisteredParticipating + psNotRegisteredSubbedIn,
                    notCurrentlyParticipating = psRegisteredNotParticipating + psNotRegisteredNotSubbedIn
                )
            )
        )
    }

    private fun getParticipantsInRound(setupRoundId: UUID): App<CompetitionSetupError, List<ParticipantForExecutionDto>> =
        KIO.comprehension {
            val setupRoundRecord = !CompetitionSetupRoundRepo.getWithMatches(setupRoundId).orDie()
                .onNullFail { CompetitionSetupError.RoundNotFound }

            val participants = setupRoundRecord.matches!!.filterNotNull().flatMap { match ->
                match.teams!!.filterNotNull().flatMap { team ->
                    team.participants!!.filterNotNull().map { participant ->
                        !participant.toParticipantParticipatingInRoundDto(team)
                    }
                }

            }

            KIO.ok(participants)
        }

    // Filter registrationParticipants that are currently subbed out (Check if the last substitution containing that participant was them being subbed OUT)
    private fun getSubbedOutParticipants(
        participants: List<ParticipantForExecutionDto>,
        substitutions: List<SubstitutionViewRecord>
    ): List<ParticipantForExecutionDto> {
        return participants.filter { regP ->
            val substitutionsContainingParticipant =
                substitutions.filter { sub -> (sub.participantOut!!.id == regP.id || sub.participantIn!!.id == regP.id) }
            if (substitutionsContainingParticipant.isEmpty()) {
                false
            } else {
                substitutionsContainingParticipant.sortedBy { it.orderForRound }
                    .last().participantOut!!.id == regP.id
            }
        }
    }

    // Get currently subbed in Participants (checks if the last substitution containing that participant was them being subbed IN)
    private fun getSubbedInParticipants(
        substitutions: List<SubstitutionViewRecord>
    ): App<Nothing, List<ParticipantForExecutionDto>> = KIO.comprehension {
        val subs =
            substitutions.filter { sub ->
                substitutions.find { s ->
                    s.participantOut!!.id == sub.participantIn!!.id && (s.orderForRound!! > sub.orderForRound!!)
                } == null
            }
        subs.traverse { sub ->
            sub.toParticipantParticipatingInRoundDto(
                sub.participantIn!!
            )
        }
    }
}