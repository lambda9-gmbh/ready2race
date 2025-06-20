package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.toCompetitionRoundDto
import de.lambda9.ready2race.backend.app.competitionExecution.entity.*
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupParticipantRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupRoundRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.applyCompetitionMatch
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupError
import de.lambda9.ready2race.backend.app.competitionMatchTeam.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.calls.requests.logger
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.*
import java.time.LocalDateTime
import java.util.UUID

object CompetitionExecutionService {

    fun createNewRound(
        competitionId: UUID,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        var createFollowingRound = true
        while (createFollowingRound) {
            val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

            val currentAndNextRound = getCurrentAndNextRound(setupRounds)

            if (currentAndNextRound.first == null) {

                val registrations = !CompetitionRegistrationRepo.getByCompetitionId(competitionId).orDie()

                !checkRoundCreation(true, setupRounds, null, currentAndNextRound.second, registrations)

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


                val seedingList = getSeedingList(nextRoundSetupMatches.map { it.teams }, registrations.size)


                val newTeamRecords = sortedRegistrations.mapIndexed { index, reg ->
                    val matchIndex = seedingList.indexOfFirst { it.contains(index + 1) }

                    val automaticFirstPlace = seedingList[matchIndex].filter { it <= registrations.size }.size == 1

                    CompetitionMatchTeamRecord(
                        id = UUID.randomUUID(),
                        competitionMatch = matchRecords[matchIndex].competitionSetupMatch,
                        competitionRegistration = reg.id,
                        startNumber = seedingList[matchIndex].indexOfFirst { it == index + 1 } + 1,
                        place = if (automaticFirstPlace) 1 else null,
                        createdAt = LocalDateTime.now(),
                        createdBy = userId,
                        updatedAt = LocalDateTime.now(),
                        updatedBy = userId,
                    )
                }
                !CompetitionMatchTeamRepo.create(newTeamRecords).orDie()

                if(newTeamRecords.size > nextRoundSetupMatches.size
                    || currentAndNextRound.second?.required == true
                    || currentAndNextRound.second?.nextRound == null){
                    createFollowingRound = false
                }

            } else {

                !checkRoundCreation(true, setupRounds, currentAndNextRound.first, currentAndNextRound.second, null)

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
                    getSeedingList(
                        currentRoundMatches.map { it.first.teams },
                        nextRoundSetupMatches.sumOf { it.teams!! })


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


                val newTeamRecords = currentTeamsToParticipantId.map { team ->

                    val prevTeam = team.first
                    val nextRoundTeam = team.second!!

                    val automaticFirstPlace =
                        currentTeamsToParticipantId.filter {
                            it.second!!.competitionSetupMatch == nextRoundTeam.competitionSetupMatch
                        }.size == 1

                    CompetitionMatchTeamRecord(
                        id = UUID.randomUUID(),
                        competitionMatch = nextRoundTeam.competitionSetupMatch!!,
                        competitionRegistration = prevTeam.competitionRegistration!!,
                        startNumber = nextRoundTeam.ranking,
                        place = if (automaticFirstPlace) 1 else null,
                        createdAt = LocalDateTime.now(),
                        createdBy = userId,
                        updatedAt = LocalDateTime.now(),
                        updatedBy = userId,
                    )
                }
                !CompetitionMatchTeamRepo.create(newTeamRecords).orDie()

                if(newTeamRecords.size > nextRoundSetupMatches.size
                    || currentAndNextRound.second?.required == true
                    || currentAndNextRound.second?.nextRound == null){
                    createFollowingRound = false
                }
            }
        }

        noData
    }

    fun getProgress(
        competitionId: UUID,
    ): App<ServiceError, ApiResponse.Dto<CompetitionExecutionProgressDto>> =
        KIO.comprehension {
            val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

            val currentAndNextRound = getCurrentAndNextRound(setupRounds)

            val registrationsIfFirst = if (currentAndNextRound.first == null) {
                !CompetitionRegistrationRepo.getByCompetitionId(competitionId).orDie()
            } else {
                null
            }

            val canNotCreateRoundReasons = !checkRoundCreation(
                false,
                setupRounds,
                currentAndNextRound.first,
                currentAndNextRound.second,
                registrationsIfFirst,
            )

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
                        canNotCreateRoundReasons,
                        lastRoundFinished
                    )
                )
            }
        }

    private fun checkRoundCreation(
        failOnError: Boolean,
        rounds: List<CompetitionSetupRoundWithMatchesRecord>,
        currentRound: CompetitionSetupRoundWithMatchesRecord?,
        nextRound: CompetitionSetupRoundWithMatchesRecord?,
        registrations: List<CompetitionRegistrationRecord>?
    ): App<CompetitionExecutionError, List<CompetitionExecutionCanNotCreateRoundReason>> = KIO.comprehension {
        val reasons = mutableListOf<Pair<CompetitionExecutionCanNotCreateRoundReason, CompetitionExecutionError>>()

        val nextRoundSetupMatches = nextRound?.setupMatches?.filterNotNull()


        if (rounds.isEmpty()) {
            reasons.add(CompetitionExecutionCanNotCreateRoundReason.NO_ROUNDS_IN_SETUP to CompetitionExecutionError.NoRoundsInSetup)
        } else {
            if (nextRound == null) {
                reasons.add(CompetitionExecutionCanNotCreateRoundReason.ALL_ROUNDS_CREATED to CompetitionExecutionError.AllRoundsCreated)
            }

            if (nextRoundSetupMatches?.isEmpty() == true)
                reasons.add(CompetitionExecutionCanNotCreateRoundReason.NO_SETUP_MATCHES to CompetitionExecutionError.NoSetupMatchesInRound)

        }


        if (currentRound == null) {
            if (registrations.isNullOrEmpty())
                reasons.add(CompetitionExecutionCanNotCreateRoundReason.NO_REGISTRATIONS to CompetitionExecutionError.NoRegistrations)
            else {
                if (nextRoundSetupMatches != null) {
                    if (nextRoundSetupMatches.find { it.teams == null } == null && nextRoundSetupMatches.sumOf {
                            it.teams ?: 0
                        } < registrations.size) {
                        reasons.add(CompetitionExecutionCanNotCreateRoundReason.NOT_ENOUGH_TEAM_SPACE to CompetitionExecutionError.NotEnoughTeamSpace)

                    }
                }

                if (registrations.none { it.teamNumber != null })
                    reasons.add(CompetitionExecutionCanNotCreateRoundReason.REGISTRATIONS_NOT_FINALIZED to CompetitionExecutionError.RegistrationsNotFinalized)
            }


        } else {
            val currentRoundPlaces =
                currentRound.matches!!.flatMap { match -> match!!.teams!!.map { team -> team!!.place } }

            if (currentRoundPlaces.contains(null))
                reasons.add(CompetitionExecutionCanNotCreateRoundReason.NOT_ALL_PLACES_SET to CompetitionExecutionError.NotAllPlacesSet)
        }

        if (failOnError && reasons.isNotEmpty()) {
            return@comprehension KIO.fail(reasons.first().second)
        } else {
            KIO.ok(
                reasons.map { it.first }
            )
        }
    }

    fun updateMatchData(
        matchId: UUID,
        userId: UUID,
        request: UpdateCompetitionMatchRequest
    ): App<CompetitionExecutionError, ApiResponse.NoData> = KIO.comprehension {

        // todo: Prevent Update if Match is not being held (one participant and round is not required)

        !CompetitionMatchRepo.update(matchId) {
            startTime = request.startTime
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().onNullFail { CompetitionExecutionError.MatchNotFound }

        val teamRecords = !CompetitionMatchTeamRepo.getByMatch(matchId).orDie()

        if (teamRecords.size != request.teams.size) {
            return@comprehension KIO.fail(CompetitionExecutionError.TeamsNotMatching)
        }
        teamRecords.forEach { tr ->
            if (request.teams.filter { it.registrationId == tr.competitionRegistration }.size != 1)
                return@comprehension KIO.fail(CompetitionExecutionError.TeamsNotMatching)
        }

        !teamRecords.traverse { team ->
            CompetitionMatchTeamRepo.update(team) {
                startNumber = (team.startNumber * -1)
            }.orDie()
        }

        !teamRecords.traverse { team ->
            CompetitionMatchTeamRepo.update(team) {
                startNumber =
                    request.teams.find { it.registrationId == team.competitionRegistration }!!.startNumber // Can be guaranteed by previous checks
                updatedBy = userId
                updatedAt = LocalDateTime.now()
            }.orDie().onNullFail { CompetitionExecutionError.MatchTeamNotFound }
        }

        noData
    }

    fun updateMatchResult(
        competitionId: UUID,
        matchId: UUID,
        userId: UUID,
        request: UpdateCompetitionMatchResultRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        // todo: Prevent Update if Match is not being held (one participant and round is not required)

        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

        val currentRound = getCurrentAndNextRound(setupRounds).first
            ?: return@comprehension KIO.fail(CompetitionExecutionError.NoRoundsInSetup)

        if (currentRound.matches!!.find { it!!.competitionSetupMatch == matchId } == null) {
            return@comprehension KIO.fail(CompetitionExecutionError.MatchResultsLocked)
        }

        request.teamResults.traverse { result ->
            CompetitionMatchTeamRepo.updateByMatchAndRegistrationId(matchId, result.registrationId) {
                place = result.place
                updatedBy = userId
                updatedAt = LocalDateTime.now()
            }.orDie().onNullFail { CompetitionExecutionError.MatchTeamNotFound }
        }.map { ApiResponse.NoData }
    }


    fun deleteCurrentRound(
        competitionId: UUID
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

        val currentRound = getCurrentAndNextRound(setupRounds).first
            ?: return@comprehension KIO.fail(CompetitionExecutionError.NoRoundsInSetup)

        val deleted = !CompetitionMatchRepo.delete(currentRound.matches!!.map { it!!.competitionSetupMatch!! }).orDie()

        if (deleted < 1) {
            KIO.fail(CompetitionExecutionError.RoundNotFound)
        } else {
            noData
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