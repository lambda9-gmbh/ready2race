package de.lambda9.ready2race.backend.app.substitution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionExecution.control.toCompetitionMatchTeamParticipant
import de.lambda9.ready2race.backend.app.competitionExecution.control.toCompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchTeamParticipant
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionTeamParticipantDto
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupRoundRepo
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupError
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantError
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.app.substitution.control.toRecord
import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionError
import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionRequest
import de.lambda9.ready2race.backend.calls.requests.logger
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.RegisteredCompetitionTeamParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionViewRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.UUID

object SubstitutionService {

    fun addSubstitution(
        userId: UUID,
        request: SubstitutionRequest
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        // todo: check for valid request

        val maxOrderForRound = !SubstitutionRepo.getByRound(request.competitionSetupRound).orDie()
            .map { subs -> subs.maxOfOrNull { it.orderForRound } }

        val record = !request.toRecord(userId, (maxOrderForRound ?: 0) + 1)

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
    ): App<CompetitionSetupError, ApiResponse.ListDto<CompetitionTeamParticipantDto>> = KIO.comprehension {
        val registrationParticipants = !getParticipantsInRound(competitionSetupRoundId)

        val substitutions = !SubstitutionRepo.getViewByRound(competitionSetupRoundId).orDie()

        val subbedOutRegistrationParticipants = getSubbedOutParticipants(registrationParticipants, substitutions)
        val subbedInParticipants = getSubbedInParticipants(substitutions).map { it.toCompetitionMatchTeamParticipant() }

        // Get registered participants that are NOT currently subbed out + currently subbed in participants that are not registered
        val currentlyParticipating =
            registrationParticipants.filter { regP ->
                subbedOutRegistrationParticipants.find { subbedOutP ->
                    subbedOutP.participantId == regP.participantId
                } == null
            } + subbedInParticipants.filter { subbedInP ->
                registrationParticipants.find { regP ->
                    regP.participantId == subbedInP.participantId
                } == null
            }

        logger.info { "Currently Participating: $currentlyParticipating" }

        // todo
        KIO.ok(ApiResponse.ListDto(emptyList()))
    }

    fun getPossibleSubstitutionsForParticipant(
        competitionSetupRoundId: UUID,
        participantId: UUID,
    ): App<ServiceError, ApiResponse.ListDto<CompetitionTeamParticipantDto>> = KIO.comprehension {

        val participant =
            !ParticipantRepo.get(participantId).orDie().onNullFail { ParticipantError.ParticipantNotFound }

        val participantsInClub = !ParticipantRepo.getByClubId(participant.club).orDie()
            .map { participants -> participants.filter { it.id != participantId } }

        val registrationParticipants = !getParticipantsInRound(competitionSetupRoundId)

        val substitutions = !SubstitutionRepo.getViewByRound(competitionSetupRoundId).orDie()

        val subbedOutRegistrationParticipants = getSubbedOutParticipants(registrationParticipants, substitutions)

        val subbedInParticipants = getSubbedInParticipants(substitutions).map { it.toCompetitionMatchTeamParticipant() }

        // When the participant is 1. participating (no subs made with that participant) 2. was NOT subbed out as their last substitution-entry 3. was subbed in as their last substitution-entry
        val currentlyParticipating = participantsInClub.filter { pInClub ->
            (registrationParticipants.find { regP -> regP.participantId == pInClub.id } != null
                && subbedOutRegistrationParticipants.find { regP -> regP.participantId == pInClub.id } == null
                && subbedInParticipants.find { regP -> regP.participantId == pInClub.id } != null)
        }

        val notCurrentlyParticipating =
            participantsInClub.filter { pInClub -> currentlyParticipating.find { cp -> cp.id == pInClub.id } == null }

        // Split Members into "currentlyParticipating" and "notCurrentlyParticipating"

        logger.info { "Currently Participating: $currentlyParticipating" }
        logger.info { "Not currently participating: $notCurrentlyParticipating" }


        // todo
        KIO.ok(ApiResponse.ListDto(emptyList()))
    }

    private fun getParticipantsInRound(setupRoundId: UUID): App<CompetitionSetupError, List<CompetitionMatchTeamParticipant>> =
        KIO.comprehension {
            val setupRoundRecord = !CompetitionSetupRoundRepo.getWithMatches(setupRoundId).orDie()
                .onNullFail { CompetitionSetupError.RoundNotFound }

            setupRoundRecord.toCompetitionSetupRoundWithMatches()
                .map { round ->
                    round.matches.flatMap { match ->
                        match.teams.flatMap { team ->
                            team.participants
                        }
                    }
                }
        }

    // Filter registrationParticipants that are currently subbed out (Check if the last substitution containing that participant was them being subbed OUT)
    private fun getSubbedOutParticipants(
        participants: List<CompetitionMatchTeamParticipant>,
        substitutions: List<SubstitutionViewRecord>
    ): List<CompetitionMatchTeamParticipant> {
        return participants.filter { regP ->
            substitutions.filter { sub -> (sub.participantOut!!.participantId == regP.participantId || sub.participantIn!!.participantId == regP.participantId) }
                .sortedBy { it.orderForRound }.last().participantOut!!.participantId == regP.participantId
        }
    }

    // Get currently subbed in Participants (checks if the last substitution containing that participant was them being subbed IN)
    private fun getSubbedInParticipants(
        substitutions: List<SubstitutionViewRecord>
    ): List<RegisteredCompetitionTeamParticipantRecord> {
        return substitutions.filter { sub ->
            substitutions.find { s ->
                s.participantOut!!.participantId == sub.participantIn!!.participantId && (s.orderForRound!! > sub.orderForRound!!)
            } == null
        }.map { sub -> sub.participantIn!! }
    }
}