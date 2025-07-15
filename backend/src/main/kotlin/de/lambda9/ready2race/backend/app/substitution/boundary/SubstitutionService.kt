package de.lambda9.ready2race.backend.app.substitution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService.getCurrentAndNextRound
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupRoundRepo
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupError
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantError
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.app.substitution.control.toParticipantParticipatingInRoundDto
import de.lambda9.ready2race.backend.app.substitution.control.toPossibleSubstitutionParticipantDto
import de.lambda9.ready2race.backend.app.substitution.control.toRecord
import de.lambda9.ready2race.backend.app.substitution.entity.*
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
        competitionId: UUID,
        request: SubstitutionRequest
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val currentSetupRound = !getCurrentRound(competitionId)

        val possibleSubsData = !getPossibleSubstitutionsHelper(
            currentSetupRound.setupRoundId,
            request.participantOut
        )


        // Check if participant OUT is available for sub out
        val availableSubOutParticipants = !getParticipantsCurrentlyParticipatingHelper(
            possibleSubsData.registrationParticipants,
            possibleSubsData.substitutions,
        )
        val pOutParticipant = availableSubOutParticipants.find { it.id == request.participantOut }
        if (pOutParticipant == null) {
            return@comprehension KIO.fail(SubstitutionError.ParticipantOutNotAvailableForSubstitution)
        }


        val maxOrderForRound = possibleSubsData.substitutions.maxOfOrNull { it.orderForRound!! }


        // Sub and swap?

        val subInCurrentlyParticipating =
            possibleSubsData.possibleSubstitutions.currentlyParticipating.find { p -> p.id == request.participantIn }
        val subInNotCurrentlyParticipating =
            possibleSubsData.possibleSubstitutions.notCurrentlyParticipating.find { p -> p.id == request.participantIn }

        if (subInCurrentlyParticipating != null) {
            // Swap

            val swapRecord1 = !request.toRecord(
                userId,
                competitionRegistrationId = pOutParticipant.competitionRegistrationId,
                competitionSetupRound = currentSetupRound.setupRoundId,
                orderForRound = (maxOrderForRound ?: 0) + 1,
                namedParticipant = pOutParticipant.namedParticipantId,
                swapPInWithPOut = false
            )
            val swapRecord2 = !request.toRecord(
                userId,
                competitionRegistrationId = subInCurrentlyParticipating.registrationId!!, // The registrationId has to be there if the subIn is currently participating
                competitionSetupRound = currentSetupRound.setupRoundId,
                orderForRound = swapRecord1.orderForRound + 1,
                namedParticipant = subInCurrentlyParticipating.namedParticipantId!!, // The namedParticipantId has to be there if the subIn is currently participating
                swapPInWithPOut = true
            )

            !SubstitutionRepo.insert(listOf(swapRecord1, swapRecord2)).orDie()
            noData

        } else if (subInNotCurrentlyParticipating != null) {
            // Sub in/out

            val record = !request.toRecord(
                userId,
                competitionRegistrationId = pOutParticipant.competitionRegistrationId,
                competitionSetupRound = currentSetupRound.setupRoundId,
                orderForRound = (maxOrderForRound ?: 0) + 1,
                namedParticipant = pOutParticipant.namedParticipantId,
                swapPInWithPOut = false
            )

            !SubstitutionRepo.create(record).orDie()
            noData

        } else {
            return@comprehension KIO.fail(
                SubstitutionError.ParticipantInNotAvailableForSubstitution
            )
        }
    }


    fun getParticipantsCurrentlyParticipatingInRound(
        competitionId: UUID,
    ): App<ServiceError, ApiResponse.ListDto<ParticipantForExecutionDto>> = KIO.comprehension {
        val currentSetupRound = !getCurrentRound(competitionId)

        val registrationParticipants = !getParticipantsInRound(currentSetupRound.setupRoundId)
        val substitutions = !SubstitutionRepo.getViewByRound(currentSetupRound.setupRoundId).orDie()

        getParticipantsCurrentlyParticipatingHelper(registrationParticipants, substitutions)
            .map { ApiResponse.ListDto(it) }
    }

    private fun getParticipantsCurrentlyParticipatingHelper(
        registrationParticipants: List<ParticipantForExecutionDto>,
        substitutions: List<SubstitutionViewRecord>,
    ): App<CompetitionSetupError, List<ParticipantForExecutionDto>> = KIO.comprehension {

        val subbedOutRegistrationParticipants = getSubbedOutParticipants(registrationParticipants, substitutions)

        // Get registered participants that are NOT currently subbed out + currently subbed in participants that are not registered
        val registeredNotSubbedOut = registrationParticipants.filter { regP ->
            subbedOutRegistrationParticipants.find { subbedOutP ->
                subbedOutP.id == regP.id
            } == null
        }

        val subbedInParticipants = !getSubbedInParticipants(substitutions)
        val subbedInNotRegistered = subbedInParticipants.filter { subbedInP ->
            registrationParticipants.find { regP ->
                regP.id == subbedInP.id
            } == null
        }

        val availableParticipantsForSubstitution = registeredNotSubbedOut + subbedInNotRegistered

        KIO.ok(availableParticipantsForSubstitution)
    }

    fun getPossibleSubstitutionsForParticipant(
        competitionId: UUID,
        participantId: UUID,
    ): App<ServiceError, ApiResponse.Dto<PossibleSubstitutionsForParticipantDto>> = KIO.comprehension {
        val currentSetupRound = !getCurrentRound(competitionId)

        val possibleSubstitutionsData = !getPossibleSubstitutionsHelper(currentSetupRound.setupRoundId, participantId)

        KIO.ok(
            ApiResponse.Dto(
                possibleSubstitutionsData.possibleSubstitutions
            )
        )
    }

    private fun getPossibleSubstitutionsHelper(
        competitionSetupRoundId: UUID,
        participantId: UUID,
    ): App<ServiceError, SubstitutionsSharedFunctionData> = KIO.comprehension {
        val participant =
            !ParticipantRepo.get(participantId).orDie().onNullFail { ParticipantError.ParticipantNotFound }


        // Registered Participants

        val registrationParticipants = !getParticipantsInRound(competitionSetupRoundId)

        val substitutions = !SubstitutionRepo.getViewByRound(competitionSetupRoundId).orDie()
        val subbedOutRegistrationParticipants = getSubbedOutParticipants(registrationParticipants, substitutions)


        val clubMembersRegistered = registrationParticipants
            .filter { it.clubId == participant.club }
            .filter { it.id != participantId }
            .map { !it.toPossibleSubstitutionParticipantDto() }

        val (psRegisteredParticipating, psRegisteredNotParticipating) = clubMembersRegistered.partition { p ->
            subbedOutRegistrationParticipants.find { it.id == p.id } == null
        }


        // Not registered Participants (club members)

        val participantsInClub = !ParticipantRepo.getByClubId(participant.club).orDie()
        val psNotRegistered = participantsInClub.filter { p ->
            clubMembersRegistered.find { regP -> regP.id == p.id } == null && p.id != participantId
        }

        val subbedInParticipants = !getSubbedInParticipants(substitutions)

        val psNotRegisteredSubbedIn =
            psNotRegistered
                .mapNotNull { p -> subbedInParticipants.find { regP -> regP.id == p.id } }
                .map { !it.toPossibleSubstitutionParticipantDto() }

        val psNotRegisteredNotSubbedIn = psNotRegistered.filter { p -> subbedInParticipants.none { it.id == p.id } }
            .map { !it.toPossibleSubstitutionParticipantDto() }



        KIO.ok(
            SubstitutionsSharedFunctionData(
                possibleSubstitutions = PossibleSubstitutionsForParticipantDto(
                    currentlyParticipating = psRegisteredParticipating + psNotRegisteredSubbedIn,
                    notCurrentlyParticipating = psRegisteredNotParticipating + psNotRegisteredNotSubbedIn
                ),
                participant = participant,
                participantsInClub = participantsInClub,
                registrationParticipants = registrationParticipants,
                substitutions = substitutions,
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
    // Except a swap (check one sub before if that was the same participant being subbed into another team)
    private fun getSubbedOutParticipants(
        participants: List<ParticipantForExecutionDto>,
        substitutions: List<SubstitutionViewRecord>
    ): List<ParticipantForExecutionDto> {
        return participants.filter { regP ->
            val substitutionsContainingParticipant =
                substitutions
                    .filter { sub -> (sub.participantOut!!.id == regP.id || sub.participantIn!!.id == regP.id) }
            if (substitutionsContainingParticipant.isNotEmpty()) {
                val lastSubOut = substitutionsContainingParticipant
                    .sortedBy { it.orderForRound }
                    .last()
                val lastSubWasASwap = getSwapSubstitution(lastSubOut, substitutionsContainingParticipant) != null

                lastSubOut.participantOut!!.id == regP.id && !lastSubWasASwap
            } else false
        }
    }

    // Get currently subbed in Participants (true if being subbed in was the last substitution that participant was part of) - Also true if the last substitution was a swap
    // This ONLY filters the substitutions, not the registrationParticipants
    private fun getSubbedInParticipants(
        substitutions: List<SubstitutionViewRecord>
    ): App<Nothing, List<ParticipantForExecutionDto>> = KIO.comprehension {

        val subIns = substitutions.filter { sub ->

            val subsWithParticipant = substitutions.filter {
                sub.participantIn!!.id == it.participantOut!!.id || sub.participantIn!!.id == it.participantIn!!.id
            }.sortedBy { it.orderForRound }

            if (subsWithParticipant.isNotEmpty()) {
                subsWithParticipant.last().participantIn == sub.participantIn || getSwapSubstitution(
                    sub,
                    subsWithParticipant
                ) != null
            } else false
        }
        subIns.traverse { sub ->
            sub.toParticipantParticipatingInRoundDto(
                sub.participantIn!!
            )
        }
    }

    // If the substitution is part of a swap - get the other substitution
    fun getSwapSubstitution(
        substitution: SubstitutionViewRecord,
        substitutions: List<SubstitutionViewRecord>
    ): UUID? {
        val subBefore = substitutions
            .find { it.orderForRound == (substitution.orderForRound!! - 1) }

        val subAfter = substitutions
            .find { it.orderForRound == (substitution.orderForRound!! + 1) }

        if (subBefore != null) {
            if (substitution.participantOut == subBefore.participantIn
                && substitution.participantIn == subBefore.participantOut
                && substitution.competitionRegistrationId != subBefore.competitionRegistrationId
            )
                return subBefore.id
        }
        if (subAfter != null) {
            if (substitution.participantOut == subAfter.participantIn
                && substitution.participantIn == subAfter.participantOut
                && substitution.competitionRegistrationId != subAfter.competitionRegistrationId
            )
                return subAfter.id
        }
        return null
    }

    fun deleteLastSubstitution(
        competitionId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val currentSetupRound = !getCurrentRound(competitionId)

        val substitutions = !SubstitutionRepo.getViewByRound(currentSetupRound.setupRoundId).orDie()
        if (substitutions.isEmpty()) return@comprehension KIO.fail(SubstitutionError.NotFound)

        val lastSubstitution = substitutions.sortedBy { it.orderForRound }.last()

        val swapSubstitution = getSwapSubstitution(lastSubstitution, substitutions)

        val deleted = !SubstitutionRepo.delete(
            listOfNotNull(
                lastSubstitution.id,
                swapSubstitution
            )
        ).orDie()

        if (deleted < 1) {
            KIO.fail(SubstitutionError.NotFound)
        } else {
            noData
        }
    }

    private fun getCurrentRound(
        competitionId: UUID,
    ): App<ServiceError, CompetitionSetupRoundWithMatches> = KIO.comprehension {
        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
        val (currentSetupRound, _) = getCurrentAndNextRound(setupRounds)

        if (currentSetupRound == null)
            return@comprehension KIO.fail(CompetitionExecutionError.RoundNotFound)
        else
            KIO.ok(currentSetupRound)
    }
}