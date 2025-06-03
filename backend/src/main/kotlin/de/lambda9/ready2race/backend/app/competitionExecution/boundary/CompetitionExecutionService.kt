package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupParticipantRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupRoundRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.applyCompetitionMatch
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupError
import de.lambda9.ready2race.backend.app.competitionMatchTeam.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.requests.logger
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.UUID

object CompetitionExecutionService {

    /*  fun canCreateNewRound(
          currentSetupRoundId: UUID
      ): App<Nothing, Boolean> = KIO.comprehension {

      }*/

    fun createNewRound(
        competitionId: UUID,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        //val competitionRecord = CompetitionRepo.getWithProperties(competitionId, scope).orDie().onNullFail { CompetitionError.CompetitionNotFound }

        val setupId = !CompetitionPropertiesRepo.getIdByCompetitionOrTemplateId(competitionId).orDie()
            .onNullFail { CompetitionSetupError.CompetitionPropertiesNotFound }

        val rounds = !CompetitionSetupRoundRepo.getWithMatchesBySetup(setupId).orDie()
        logger.info { "rounds $rounds" }

        if (rounds.isEmpty()) {
            logger.error { "Error 1" }
            return@comprehension KIO.fail(CompetitionExecutionError.NoSetupRoundFound)
        }

        val finalRound = rounds.find { it.nextRound == null }
        var currentRound = finalRound
        for (i in rounds) {
            if (currentRound?.matches?.isEmpty() == true) {
                currentRound = rounds.find { it.nextRound == currentRound?.setupRoundId }
            }
        }
        logger.info { "Current Round $currentRound" }

        if (currentRound == null) {
            // First Round
            val nextRound = rounds.find { r1 -> rounds.find { r2 -> r1.setupRoundId == r2.nextRound } == null }
            if (nextRound == null) {
                logger.error { "Error 2" }
                return@comprehension KIO.fail(CompetitionExecutionError.NoSetupRoundFound)
            }

            val nextRoundSetupMatches = nextRound.setupMatches!!.filterNotNull().sortedBy { it.weighting }

            // todo Check CanCreateNewRound

            val matchRecords = nextRoundSetupMatches.map {
                !it.applyCompetitionMatch(userId, null)
            }
            !CompetitionMatchRepo.create(matchRecords).orDie()

            val registrations = !CompetitionRegistrationRepo.getByCompetitionId(competitionId).orDie()

            // todo: Leihboote zu beachten?


            // todo: registrations auf matches aufteilen
            val newTeamRecords = registrations.shuffled().map{
                CompetitionMatchTeamRecord(

                )
            }

        } else {
            // Following Round
            val nextRound = rounds.find { it.setupRoundId == currentRound.nextRound }
            if (nextRound == null) {
                logger.error { "Error 2" }
                return@comprehension KIO.fail(CompetitionExecutionError.NoSetupRoundFound)
            }

            val currentRoundSetupMatches = currentRound.setupMatches!!.filterNotNull().sortedBy { it.weighting }
            val nextRoundSetupMatches = nextRound.setupMatches!!.filterNotNull().sortedBy { it.weighting }


            // todo Check CanCreateNewRound

            val matchRecords = nextRoundSetupMatches.map {
                !it.applyCompetitionMatch(userId, null)
            }
            !CompetitionMatchRepo.create(matchRecords).orDie()

            val currentRoundTeams = !CompetitionMatchTeamRepo
                .get(currentRound.matches!!.map { it!!.competitionSetupMatch })
                .orDie()

            val currentRoundOutcomes = getOutcomes(currentRoundSetupMatches, nextRoundSetupMatches)
            logger.info { "Current round outcomes: $currentRoundOutcomes" }

            val currentTeamsSortedByMatches =
                currentRoundSetupMatches.map { match ->
                    match to currentRoundTeams.filter { it.competitionMatch == match.id }.sortedBy { it.place }
                }

            logger.info { "currentTeamsSortedByMatches: $currentTeamsSortedByMatches" }

            val foo = currentRoundOutcomes
                .mapIndexed { outcomesIndex, outcomes ->
                    currentTeamsSortedByMatches[outcomesIndex]
                        .second
                        .mapIndexed { teamIndex, team -> team to outcomes[teamIndex] }
                }.flatten()

            logger.info { "Mapped Teams and Outcomes: $foo" }

            val nextRoundSetupParticipants =
                !CompetitionSetupParticipantRepo.get(nextRoundSetupMatches.map { it.id }).orDie()
            logger.info { "nextRoundSetupParticipants: $nextRoundSetupParticipants" }

        }


        noData
    }

    private fun getOutcomes(
        currentRoundSetupMatches: List<CompetitionSetupMatchRecord>,
        nextRoundSetupMatches: List<CompetitionSetupMatchRecord>
    ): List<List<Int>> {
        val nextRoundTeams = nextRoundSetupMatches.sumOf { it.teams!! }
        val currentRoundHighestTeamCount =
            getHighestTeamCount(currentRoundSetupMatches.map { it.teams }, nextRoundTeams)

        val currentRoundSeedings = currentRoundSetupMatches.map { mutableListOf<Int>() }

        var seedingsTaken = 0
        for (i in 0..<currentRoundHighestTeamCount) {
            fun addToList(index: Int) {
                if ((currentRoundSetupMatches[index].teams
                        ?: 0) > currentRoundSeedings[index].size || (currentRoundSetupMatches[index].teams == null && seedingsTaken < nextRoundTeams)
                ) {
                    seedingsTaken++
                    currentRoundSeedings[index].add(seedingsTaken)
                }
            }

            if (i % 2 == 0) {
                for (s in currentRoundSetupMatches.indices) addToList(s)
            } else {
                for (s in currentRoundSetupMatches.size - 1 downTo 0) addToList(s)
            }
        }

        return currentRoundSeedings
    }

    private fun getHighestTeamCount(
        teams: List<Int?>,
        nextRoundTeams: Int
    ): Int {
        val highestDefinedTeamCount = teams.maxBy { it ?: 0 } ?: 0

        val teamsForEachUndefinedTeams = if (teams.any { it == null }) {
            nextRoundTeams - teams.filterNotNull().sum() / teams.filter { it == null }.size
        } else 0

        return if (highestDefinedTeamCount > teamsForEachUndefinedTeams) {
            highestDefinedTeamCount
        } else {
            teamsForEachUndefinedTeams
        }
    }
}