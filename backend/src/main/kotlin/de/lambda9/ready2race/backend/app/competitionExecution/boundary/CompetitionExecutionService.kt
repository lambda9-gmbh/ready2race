package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.toCompetitionRoundDto
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionProgressDto
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupParticipantRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupRoundRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.applyCompetitionMatch
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupError
import de.lambda9.ready2race.backend.app.competitionMatchTeam.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.calls.requests.logger
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.recoverDefault
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.UUID

object CompetitionExecutionService {

    private fun checkRoundCreation(
        rounds: List<CompetitionSetupRoundWithMatchesRecord>,
        currentRound: CompetitionSetupRoundWithMatchesRecord?,
        nextRound: CompetitionSetupRoundWithMatchesRecord?,
        registrations: List<CompetitionRegistrationRecord>?
    ): App<CompetitionExecutionError, Unit> = KIO.comprehension {
        if (rounds.isEmpty())
            return@comprehension KIO.fail(CompetitionExecutionError.NoRoundsInSetup)
        else if (nextRound == null)
            return@comprehension KIO.fail(CompetitionExecutionError.FinalRoundAlreadyCreated)

        val nextRoundSetupMatches = nextRound.setupMatches!!.filterNotNull()

        if (nextRoundSetupMatches.isEmpty())
            return@comprehension KIO.fail(CompetitionExecutionError.NoSetupMatchesInRound)


        if (currentRound == null) {
            if (registrations.isNullOrEmpty())
                return@comprehension KIO.fail(CompetitionExecutionError.NoRegistrations)

            if (nextRoundSetupMatches.find { it.teams == null } == null && nextRoundSetupMatches.sumOf {
                    it.teams ?: 0
                } < registrations.size) {
                return@comprehension KIO.fail(CompetitionExecutionError.NotEnoughTeamSpace)
            }
        } else {
            val currentRoundPlaces =
                currentRound.matches!!.flatMap { match -> match!!.teams!!.map { team -> team!!.place } }

            if (currentRoundPlaces.contains(null))
                return@comprehension KIO.fail(CompetitionExecutionError.NotAllPlacesSet)

        }

        KIO.unit
    }

    fun createNewRound(
        competitionId: UUID,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val setupId = !CompetitionPropertiesRepo.getIdByCompetitionOrTemplateId(competitionId).orDie()
            .onNullFail { CompetitionSetupError.CompetitionPropertiesNotFound }

        val rounds = !CompetitionSetupRoundRepo.getWithMatchesBySetup(setupId).orDie()

        val currentAndNextRound = getCurrentAndNextRound(rounds)
        logger.info { "Current and next Round $currentAndNextRound" }

        if (currentAndNextRound.first == null) {

            val registrations = !CompetitionRegistrationRepo.getByCompetitionId(competitionId).orDie()

            !checkRoundCreation(rounds, null, currentAndNextRound.second, registrations)

            val nextRoundSetupMatches =
                currentAndNextRound.second!!.setupMatches!!.filterNotNull().sortedBy { it.weighting }

            val sortedRegistrations = registrations
                .filter { it.teamNumber != null } // Registrations without teamNumber are ignored. A confirmation Dialog makes aware of this behaviour
                .sortedBy { it.teamNumber }


            val matchRecords = nextRoundSetupMatches
                .filterIndexed { index, _ -> index < sortedRegistrations.size }
                .map {
                    !it.applyCompetitionMatch(userId, null)
                }
            !CompetitionMatchRepo.create(matchRecords).orDie()

            // todo: Leihboote zu beachten?

            val seedingList = getSeedingList(nextRoundSetupMatches.map { it.teams }, registrations.size)

            val newTeamRecords = sortedRegistrations.mapIndexed { index, reg ->

                val matchIndex = seedingList.indexOfFirst { it.contains(index + 1) }

                CompetitionMatchTeamRecord(
                    id = UUID.randomUUID(),
                    competitionMatch = matchRecords[matchIndex].competitionSetupMatch,
                    competitionRegistration = reg.id,
                    startNumber = seedingList[matchIndex].indexOfFirst { it == index + 1 } + 1,
                    place = null,
                    createdAt = LocalDateTime.now(),
                    createdBy = userId,
                    updatedAt = LocalDateTime.now(),
                    updatedBy = userId,
                )
            }
            logger.info { "Team Records: $newTeamRecords" }
            !CompetitionMatchTeamRepo.create(newTeamRecords).orDie()


        } else {

            !checkRoundCreation(rounds, currentAndNextRound.first, currentAndNextRound.second, null)

            // todo ?? Automatic skip round if it is optional and just 1-Participant-Matches? - (Frontend Dialog?)


            // --- Collect current round data

            val currentRoundMatches = currentAndNextRound.first!!.setupMatches!!
                .filterNotNull()
                .sortedBy { it.weighting }
                .map { setupMatch ->
                    setupMatch to currentAndNextRound.first!!.matches!!.find { match -> match!!.competitionSetupMatch == setupMatch.id }
                }
            val nextRoundSetupMatches =
                currentAndNextRound.second!!.setupMatches!!.filterNotNull().sortedBy { it.weighting }

            val currentRoundOutcomes =
                getSeedingList(currentRoundMatches.map { it.first.teams }, nextRoundSetupMatches.sumOf { it.teams!! })


            val currentTeamsWithOutcome =
                currentRoundMatches.filter { it.second != null }.mapIndexed { matchIdx, match ->
                    match.second!!.teams!!.sortedBy { team -> team!!.place }.mapIndexed { teamIdx, team ->
                        team!! to currentRoundOutcomes[matchIdx][teamIdx]
                    }
                }.flatten()

            // --- Create next round

            val matchRecords = nextRoundSetupMatches
                .filterIndexed { index, _ -> index < currentTeamsWithOutcome.size }
                .map {
                    !it.applyCompetitionMatch(userId, null)
                }
            !CompetitionMatchRepo.create(matchRecords).orDie()

            val nextRoundSetupParticipants =
                !CompetitionSetupParticipantRepo.get(nextRoundSetupMatches.map { it.id }).orDie()

            val currentTeamsToParticipantId = currentTeamsWithOutcome.map { cTeam ->
                cTeam.first to nextRoundSetupParticipants.find { p -> p.seed == cTeam.second } // Match Outcomes with ParticipantSeeds
            }.filter { team -> team.second != null } // Filter teams that have not made it to next round


            val newTeamRecords = currentTeamsToParticipantId.map { oldTeam ->

                CompetitionMatchTeamRecord(
                    id = UUID.randomUUID(),
                    competitionMatch = oldTeam.second!!.competitionSetupMatch!!,
                    competitionRegistration = oldTeam.first.competitionRegistration!!,
                    startNumber = oldTeam.second!!.ranking,
                    place = null,
                    createdAt = LocalDateTime.now(),
                    createdBy = userId,
                    updatedAt = LocalDateTime.now(),
                    updatedBy = userId,
                )
            }
            logger.info { "New Team Records: $newTeamRecords" }
            !CompetitionMatchTeamRepo.create(newTeamRecords).orDie()

        }

        noData
    }

    fun getProgress(
        competitionId: UUID,
    ): App<CompetitionSetupError, ApiResponse.Dto<CompetitionExecutionProgressDto>> =
        KIO.comprehension {
            val setupId = !CompetitionPropertiesRepo.getIdByCompetitionOrTemplateId(competitionId).orDie()
                .onNullFail { CompetitionSetupError.CompetitionPropertiesNotFound }

            val setupRounds = !CompetitionSetupRoundRepo.getWithMatchesBySetup(setupId).orDie()

            val currentAndNextRound = getCurrentAndNextRound(setupRounds)

            val registrationsIfFirst = if (currentAndNextRound.first == null) {
                !CompetitionRegistrationRepo.getByCompetitionId(competitionId).orDie()
            } else {
                null
            }

            val canCreateNextRound =
                !checkRoundCreation(
                    setupRounds,
                    currentAndNextRound.first,
                    currentAndNextRound.second,
                    registrationsIfFirst
                )
                    .map { true }
                    .recoverDefault { false }

            val lastRoundFinished =
                if (currentAndNextRound.second == null && currentAndNextRound.first != null) {
                    currentAndNextRound.first!!.matches!!.flatMap { match -> match!!.teams!!.filter { it!!.place == null } }
                        .isEmpty()
                } else false

            val sortedRounds: MutableList<CompetitionSetupRoundWithMatchesRecord> = mutableListOf()
            fun addRoundToSortedList(r: CompetitionSetupRoundWithMatchesRecord?) {
                if (r != null) {
                    sortedRounds.addFirst(r)

                    addRoundToSortedList(setupRounds.firstOrNull { it.nextRound == r.setupRoundId })
                }
            }
            addRoundToSortedList(setupRounds.firstOrNull { it.nextRound == null })


            sortedRounds.filter { it.matches!!.isNotEmpty() }.traverse { round ->
                round.toCompetitionRoundDto()
            }.map {
                ApiResponse.Dto(
                    CompetitionExecutionProgressDto(
                        rounds = it,
                        lastRoundFinished = lastRoundFinished,
                        canCreateNewRound = canCreateNextRound
                    )
                )
            }
        }

    private fun getCurrentAndNextRound(
        rounds: List<CompetitionSetupRoundWithMatchesRecord>
    ): Pair<CompetitionSetupRoundWithMatchesRecord?, CompetitionSetupRoundWithMatchesRecord?> {
        val finalRound = rounds.find { it.nextRound == null }
        var currentRound = finalRound
        for (i in rounds) {
            if (currentRound?.matches?.isEmpty() == true) {
                currentRound = rounds.find { it.nextRound == currentRound?.setupRoundId }
            }
        }

        val nextRound = if (currentRound == null) {
            rounds.find { r1 -> rounds.find { r2 -> r1.setupRoundId == r2.nextRound } == null }
        } else {
            rounds.find { it.setupRoundId == currentRound.nextRound }
        }

        return currentRound to nextRound
    }

    private fun getSeedingList(
        currentRoundSetupTeams: List<Int?>,
        maxTeamsNeeded: Int
    ): List<List<Int>> {
        val currentRoundHighestTeamCount =
            getHighestTeamCount(currentRoundSetupTeams, maxTeamsNeeded)

        val currentRoundSeedings = currentRoundSetupTeams.map { mutableListOf<Int>() }

        var seedingsTaken = 0
        for (i in 0..<currentRoundHighestTeamCount) {
            fun addToList(index: Int) {
                if ((currentRoundSetupTeams[index]
                        ?: 0) > currentRoundSeedings[index].size || (currentRoundSetupTeams[index] == null && seedingsTaken < maxTeamsNeeded)
                ) {
                    seedingsTaken++
                    currentRoundSeedings[index].add(seedingsTaken)
                }
            }

            if (i % 2 == 0) {
                for (s in currentRoundSetupTeams.indices) addToList(s)
            } else {
                for (s in currentRoundSetupTeams.size - 1 downTo 0) addToList(s)
            }
        }

        return currentRoundSeedings
    }

    private fun getHighestTeamCount(
        teams: List<Int?>,
        maxTeamsNeeded: Int
    ): Int {
        val highestDefinedTeamCount = teams.maxBy { it ?: 0 } ?: 0

        val teamsForEachUndefinedTeams = if (teams.any { it == null }) {
            maxTeamsNeeded - teams.filterNotNull().sum() / teams.filter { it == null }.size
        } else 0

        return if (highestDefinedTeamCount > teamsForEachUndefinedTeams) {
            highestDefinedTeamCount
        } else {
            teamsForEachUndefinedTeams
        }
    }
}