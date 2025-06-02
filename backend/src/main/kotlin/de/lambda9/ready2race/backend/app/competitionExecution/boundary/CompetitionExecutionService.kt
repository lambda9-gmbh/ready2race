package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionExecutionRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.applyCompetitionMatch
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupParticipantRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupRoundRepo
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupError
import de.lambda9.ready2race.backend.app.competitionTeam.control.CompetitionTeamRepo
import de.lambda9.ready2race.backend.calls.requests.logger
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundWithMatchesRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.UUID
import kotlin.math.log

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
logger.info { "setupId $setupId" }

        val rounds = !CompetitionSetupRoundRepo.getWithMatchesBySetup(setupId).orDie()
        logger.info { "rounds $rounds" }

        val finalRound = rounds.find { it.nextRound == null }
        logger.info { "finalRound $finalRound" }
        if (finalRound == null) {
            logger.info { "Error 1" }
            return@comprehension KIO.fail(CompetitionExecutionError.NoSetupRoundFound)
        }
        var currentRound: CompetitionSetupRoundWithMatchesRecord = finalRound

        for (i in rounds) {
            if (currentRound.matches!!.isEmpty()) {
                val prevR = rounds.find { it.nextRound == currentRound.setupRoundId }
                if(prevR != null){
                    currentRound = prevR
                }
            }
        }
        logger.info { "Current Round $currentRound" }
        val nextRound = rounds.find { it.nextRound == currentRound.setupRoundId }
        logger.info { "nextRound $nextRound" }
        if (nextRound == null) {
            logger.info { "Error 2" }
            return@comprehension KIO.fail(CompetitionExecutionError.NoSetupRoundFound)
        }


        val currentRoundSetupMatches = currentRound.setupMatches!!.filterNotNull().sortedBy { it.weighting }
        val nextRoundSetupMatches = nextRound.setupMatches!!.filterNotNull().sortedBy { it.weighting }

        // todo Check CanCreateNewRound

        val matchRecords = nextRound.setupMatches!!.map {
            !it!!.applyCompetitionMatch(userId, null)
        }
        !CompetitionExecutionRepo.create(matchRecords).orDie()


        // todo case: first round
        val currentRoundTeams =
            !CompetitionTeamRepo.get(currentRound.matches!!.map { it!!.competitionSetupMatch }).orDie()


        val setupParticipants = !CompetitionSetupParticipantRepo.get(nextRound.setupMatches!!.map { it!!.id }).orDie()

        nextRound.setupMatches!!.forEach { setupMatch ->
            val matchParticipants = setupParticipants.filter { it.competitionSetupMatch == setupMatch!!.id }

        }


        val currentRoundOutcomes = getOutcomes(currentRoundSetupMatches, nextRoundSetupMatches)

        logger.info { "Current round setup matches: $currentRoundSetupMatches" }

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
                if (currentRoundSetupMatches[index].teams!! > currentRoundSeedings[index].size || (currentRoundSetupMatches[index].teams == null && seedingsTaken < nextRoundTeams)) {
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

        val teamsForEachUndefinedTeams = if (teams.any { it == 0 }) {
            nextRoundTeams - teams.filterNotNull().sum() / teams.filter { it == 0 }.size
        } else 0

        return if (highestDefinedTeamCount > teamsForEachUndefinedTeams) {
            highestDefinedTeamCount
        } else {
            teamsForEachUndefinedTeams
        }
    }
}