package de.lambda9.ready2race.backend.app.substitution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService.getCurrentAndNextRound
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupRoundRepo
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupError
import de.lambda9.ready2race.backend.app.eventRegistration.boundary.EventRegistrationService.checkEnoughMixedSpots
import de.lambda9.ready2race.backend.app.namedParticipant.control.NamedParticipantForCompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantRequirements
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantError
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.app.substitution.control.toParticipantForExecutionDto
import de.lambda9.ready2race.backend.app.substitution.control.toPossibleSubstitutionParticipantDto
import de.lambda9.ready2race.backend.app.substitution.control.toRecord
import de.lambda9.ready2race.backend.app.substitution.entity.*
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.NamedParticipantForCompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionViewRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.UUID

object SubstitutionService {

    fun addSubstitution(
        userId: UUID,
        competitionId: UUID,
        request: SubstitutionRequest
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        // todo: Only allow subs in correct gender

        val currentSetupRound = !getCurrentRound(competitionId)

        val possibleSubsData = !getPossibleSubstitutionsHelper(
            competitionId,
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


        // todo: Get ParticipantRequirements for pOut
        // todo: Check if all of them are listed in request.
        // todo: SubstitutionHasParticipantRequirementRepo.insert(request.participantRequirements)


        // Sub and swap?

        val subInCurrentlyParticipating =
            possibleSubsData.possibleSubstitutions.currentlyParticipating.find { p -> p.id == request.participantIn }
        val subInNotCurrentlyParticipating =
            possibleSubsData.possibleSubstitutions.notCurrentlyParticipating.find { p -> p.id == request.participantIn }

        if (subInCurrentlyParticipating != null) {
            // Swap

            // todo: Get ParticipantRequirements for subInCurrentlyParticipating
            // todo: Check if all of them are listed in request.
            // todo: SubstitutionHasParticipantRequirementRepo.insert(request.swappedParticipantRequirements)

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
        }.map { rP ->
            // Almost identical with mapping in getPossibleSubstitutionsHelper
            // Assign new values to registration and namedParticipant if the registered participant is subbed in (could be in another team/registration)
            val sortedSubInsWithRP = substitutions.sortedBy { it.orderForRound }.filter { s ->
                s.participantIn!!.id == rP.id
            }
            if (sortedSubInsWithRP.isNotEmpty()) {
                !sortedSubInsWithRP.last().toParticipantForExecutionDto(rP)
            } else {
                rP
            }
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

        val possibleSubstitutionsData =
            !getPossibleSubstitutionsHelper(competitionId, currentSetupRound.setupRoundId, participantId)

        KIO.ok(
            ApiResponse.Dto(
                possibleSubstitutionsData.possibleSubstitutions
            )
        )
    }

    private fun getPossibleSubstitutionsHelper(
        competitionId: UUID,
        competitionSetupRoundId: UUID,
        participantId: UUID,
    ): App<ServiceError, SubstitutionsSharedFunctionData> = KIO.comprehension {
        val participant =
            !ParticipantRepo.get(participantId).orDie().onNullFail { ParticipantError.ParticipantNotFound }

        val substitutions = !SubstitutionRepo.getViewByRound(competitionSetupRoundId).orDie()
        val registrationParticipants = !getParticipantsInRound(competitionSetupRoundId)
        val subbedInParticipants = !getSubbedInParticipants(substitutions)

        val participantWithData = registrationParticipants.find { it.id == participantId }
            ?: subbedInParticipants.find { it.id == participantId }!!


        val namedParticipantsForCompetition =
            !NamedParticipantForCompetitionPropertiesRepo.getByCompetition(competitionId).orDie()

        val actualRegistrationTeams =
            registrationParticipants
                .groupBy { it.competitionRegistrationId }
                .map { it.key }
                .associateWith { registrationId ->
                    !CompetitionExecutionService.getActuallyParticipatingParticipants(
                        teamParticipants = registrationParticipants.filter { it.competitionRegistrationId == registrationId },
                        substitutionsForRegistration = substitutions.filter { it.competitionRegistrationId == registrationId }
                    )
                }

        // Registered Participants

        val subbedOutRegistrationParticipants = getSubbedOutParticipants(registrationParticipants, substitutions)


        val clubMembersRegistered = registrationParticipants
            .filter { it.clubId == participant.club }
            .filter { it.id != participantId }
            .map { !it.toPossibleSubstitutionParticipantDto() }

        val (psRegisteredPart, psRegisteredNotParticipating) = !clubMembersRegistered
            .filterGender(
                requirements = namedParticipantsForCompetition,
                participantOut = !participantWithData.toPossibleSubstitutionParticipantDto(),
                actualRegistrationTeams = actualRegistrationTeams
            ).map { ps ->
                ps.partition { p ->
                    subbedOutRegistrationParticipants.find { it.id == p.id } == null
                }
            }

        // Almost identical with mapping in getParticipantsCurrentlyParticipatingHelper
        // Assign new values to registration and namedParticipant if the registered participant is subbed in (could be in another team/registration)
        val psRegisteredParticipating = psRegisteredPart.map { rP ->
            val sortedSubInsWithRP = substitutions.sortedBy { it.orderForRound }.filter { s ->
                s.participantIn!!.id == rP.id
            }
            if (sortedSubInsWithRP.isNotEmpty()) {
                !sortedSubInsWithRP.last().toPossibleSubstitutionParticipantDto(rP)
            } else {
                rP
            }
        }.filterSameRoleInSameTeam(participantWithData)


        // Not registered Participants (club members)

        val participantsInClub = !ParticipantRepo.getByClubId(participant.club).orDie()
        val psNotRegistered = participantsInClub.filter { p ->
            clubMembersRegistered.find { regP -> regP.id == p.id } == null && p.id != participantId
        }


        val psNotRegisteredSubbedIn = !psNotRegistered
            .mapNotNull { p -> subbedInParticipants.find { regP -> regP.id == p.id } }
            .map { !it.toPossibleSubstitutionParticipantDto() }
            .filterSameRoleInSameTeam(participantWithData)
            .filterGender(
                requirements = namedParticipantsForCompetition,
                participantOut = !participantWithData.toPossibleSubstitutionParticipantDto(),
                actualRegistrationTeams
            )


        val psNotRegisteredNotSubbedIn = !psNotRegistered.filter { p -> subbedInParticipants.none { it.id == p.id } }
            .map { !it.toPossibleSubstitutionParticipantDto() }
            .filterGender(
                requirements = namedParticipantsForCompetition,
                participantOut = !participantWithData.toPossibleSubstitutionParticipantDto(),
                actualRegistrationTeams
            )




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
                        !participant.toParticipantForExecutionDto(
                            clubId = team.clubId!!,
                            clubName = team.clubName!!,
                            registrationId = team.competitionRegistration!!,
                            registrationName = team.registrationName,
                        )
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
        val sortedSubs = substitutions.sortedBy { it.orderForRound }
        val uniqueSubIns =
            sortedSubs.filter { subIn -> // These are the last times that the subIn was subbedIn (highest orderForRound)
                sortedSubs.none { (it.participantIn!!.id == subIn.participantIn!!.id) && it.orderForRound!! > subIn.orderForRound!! }
            }
        KIO.ok(
            uniqueSubIns.filter { subIn ->
                val subInId = subIn.participantIn!!.id
                val subsWithParticipantIn = sortedSubs.filter {
                    subInId == it.participantOut!!.id || subInId == it.participantIn!!.id
                }
                if (subsWithParticipantIn.isNotEmpty()) {
                    // Either the last substitution of this subIn was being subbedIn (and not out) OR the last substitution of this subIn was a swap
                    (subsWithParticipantIn.last().id == subIn.id)
                        || (getSwapSubstitution(subsWithParticipantIn.last(), subsWithParticipantIn) != null)
                } else false

            }.map { sub ->
                !sub.toParticipantForExecutionDto(
                    sub.participantIn!!
                )
            }
        )
    }

    // If the substitution is part of a swap - get the other substitution
    fun getSwapSubstitution(
        substitution: SubstitutionViewRecord,
        substitutions: List<SubstitutionViewRecord>
    ): UUID? {
        fun checkSwap(sub: SubstitutionViewRecord?): UUID? {
            if (sub != null) {
                if (substitution.participantOut!!.id == sub.participantIn!!.id
                    && substitution.participantIn!!.id == sub.participantOut!!.id
                    && (substitution.competitionRegistrationId != sub.competitionRegistrationId || substitution.namedParticipantId != sub.namedParticipantId)
                ) {
                    return sub.id
                }
            }
            return null
        }

        val swapBefore = checkSwap(substitutions.find { it.orderForRound == (substitution.orderForRound!! - 1) })
        val swapAfter = checkSwap(substitutions.find { it.orderForRound == (substitution.orderForRound!! + 1) })

        return swapBefore ?: swapAfter
    }

    fun deleteSubstitution(
        competitionId: UUID,
        substitutionId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val currentSetupRound = !getCurrentRound(competitionId)

        val substitutions = !SubstitutionRepo.getViewByRound(currentSetupRound.setupRoundId).orDie()
        val substitution = substitutions.find { it.id == substitutionId }
        if (substitution == null) {
            return@comprehension KIO.fail(SubstitutionError.NotFound)
        }

        val swapSubstitution = getSwapSubstitution(substitution, substitutions)
        val swapRecord = substitutions.find { it.id == swapSubstitution }
        val order = if (swapRecord != null && substitution.orderForRound!! < swapRecord.orderForRound!!) {
            swapRecord.orderForRound!!
        } else {
            substitution.orderForRound!!
        }

        !KIO.failOn(substitutions.any {
            it.orderForRound!! > order && (it.competitionRegistrationId == substitution.competitionRegistrationId
                || it.competitionRegistrationId == swapRecord?.competitionRegistrationId)
        }) { SubstitutionError.DependentSubstitutionFound }


        val deleted = !SubstitutionRepo.delete(
            listOfNotNull(
                substitution.id,
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

    private fun List<PossibleSubstitutionParticipantDto>.filterSameRoleInSameTeam(participant: ParticipantForExecutionDto): List<PossibleSubstitutionParticipantDto> {
        return this.filter { p -> !(p.registrationId == participant.competitionRegistrationId && p.namedParticipantId == participant.namedParticipantId) }
    }

    private fun List<PossibleSubstitutionParticipantDto>.filterGender(
        requirements: List<NamedParticipantForCompetitionPropertiesRecord>,
        participantOut: PossibleSubstitutionParticipantDto,
        actualRegistrationTeams: Map<UUID, List<ParticipantForExecutionDto>>,
    ): App<Nothing, List<PossibleSubstitutionParticipantDto>> = KIO.comprehension {
        KIO.ok(
            this@filterGender.filter { p ->
                !p.checkGender(requirements, participantOut, actualRegistrationTeams, p.namedParticipantId != null)
            }
        )
    }

    // This checks a certain role in a team for the gender requirements (team and role of pOut)
    // If the genders are correct after pOut was removed and pIn was added to the counts it is valid
    // When reverseCheckForPInTeam is active, this same check will also be done the other way around - this is necessary so a swap is valid for both teams of pIn and POut
    private fun PossibleSubstitutionParticipantDto.checkGender(
        requirements: List<NamedParticipantForCompetitionPropertiesRecord>,
        participantOut: PossibleSubstitutionParticipantDto,
        actualRegistrationTeams: Map<UUID, List<ParticipantForExecutionDto>>,
        reverseCheckForPInTeam: Boolean,
    ): App<Nothing, Boolean> = KIO.comprehension {
        val genderPIn = this@checkGender.gender
        val genderPOut = participantOut.gender


        val pOutRequirements = requirements.first { it.id == participantOut.namedParticipantId }

        val countByGender: MutableMap<Gender, Int> = mutableMapOf(
            Gender.M to 0,
            Gender.F to 0,
            Gender.D to 0,
        )

        val pOutCurrentRegistrationId = actualRegistrationTeams.toList()
            .first { registrations ->
                registrations.second.map { participants -> participants.id }.contains(participantOut.id)
            }.first
        val actualPOutTeam = actualRegistrationTeams[pOutCurrentRegistrationId]!!

        actualPOutTeam
            .filter { it.namedParticipantId == pOutRequirements.id }
            .forEach { teamP ->
                countByGender[teamP.gender] = countByGender[teamP.gender]!! + 1
            }

        countByGender[genderPIn] = countByGender[genderPIn]!! + 1
        countByGender[genderPOut] = countByGender[genderPOut]!! - 1


        val enoughMixedSlots = checkEnoughMixedSpots(
            requirements = NamedParticipantRequirements(
                countMales = pOutRequirements.countMales!!,
                countFemales = pOutRequirements.countFemales!!,
                countNonBinary = pOutRequirements.countNonBinary!!,
                countMixed = pOutRequirements.countMixed!!,
            ),
            counts = countByGender
        )


        val enoughMixedSlotsInPOutTeam = if (this@checkGender.registrationId != null && reverseCheckForPInTeam) {
            !participantOut.checkGender(
                requirements,
                participantOut = this@checkGender,
                actualRegistrationTeams,
                false,
            )
        } else true

        KIO.ok(enoughMixedSlots && enoughMixedSlotsInPOutTeam)
    }
}