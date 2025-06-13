package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
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
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
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
            logger.error { "Error (Keine Runden)" }
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
            // First Round - The round that is not referenced by another "nextRound"
            val nextRound = rounds.find { r1 -> rounds.find { r2 -> r1.setupRoundId == r2.nextRound } == null }
            if (nextRound == null) {
                logger.error { "Error (erste Runde nicht gefunden)" }
                return@comprehension KIO.fail(CompetitionExecutionError.NoSetupRoundFound)
            }

            val nextRoundSetupMatches = nextRound.setupMatches!!.filterNotNull().sortedBy { it.weighting }

            // todo Check CanCreateNewRound

            val registrations = !CompetitionRegistrationRepo.getByCompetitionId(competitionId).orDie()
            val shuffledRegistrations = registrations
                .filter { it.teamNumber != null } // Registrations without teamNumber are ignored. A confirmation Dialog makes aware of this behaviour
                .sortedBy { it.teamNumber }

            logger.info { "Shuffled Registrations: $shuffledRegistrations" }

            if (shuffledRegistrations.isEmpty()) {
                logger.error { "Error (keine Registrierungen)" }
                return@comprehension KIO.fail(CompetitionExecutionError.NoRegistrations)
            }

            if (nextRoundSetupMatches.find { it.teams == null } == null && nextRoundSetupMatches.sumOf {
                    it.teams ?: 0
                } < shuffledRegistrations.size) {
                logger.error { "Error (nicht genug Setup Teams / zu viele Registrierungen)" }
                return@comprehension KIO.fail(CompetitionExecutionError.NotEnoughSetupTeams)
            }

            val matchRecords = nextRoundSetupMatches
                .filterIndexed { index, _ -> index < shuffledRegistrations.size }
                .map {
                    !it.applyCompetitionMatch(userId, null)
                }
            !CompetitionMatchRepo.create(matchRecords).orDie()

            logger.info { "Matches: $matchRecords" }

            // todo: Leihboote zu beachten?

            val seedingList = getSeedingList(nextRoundSetupMatches, registrations.size)

            val newTeamRecords = shuffledRegistrations.mapIndexed { index, reg ->

                val match = matchRecords[seedingList.indexOfFirst { it.contains(index + 1) }]

                CompetitionMatchTeamRecord(
                    id = UUID.randomUUID(),
                    competitionMatch = match.competitionSetupMatch,
                    competitionRegistration = reg.id,
                    place = null,
                    createdAt = LocalDateTime.now(),
                    createdBy = userId,
                    updatedAt = LocalDateTime.now(),
                    updatedBy = userId,
                )
            }
            !CompetitionMatchTeamRepo.create(newTeamRecords).orDie()

            logger.info { "Team Records: $newTeamRecords" }

        } else {
            // Following Round
            val nextRound = rounds.find { it.setupRoundId == currentRound.nextRound }
            if (nextRound == null) {
                logger.error { "Error (letzte Runde bereits erstellt)" }
                return@comprehension KIO.fail(CompetitionExecutionError.NoSetupRoundFound)
            }

            val currentRoundSetupMatches = currentRound.setupMatches!!.filterNotNull().sortedBy { it.weighting }
            val nextRoundSetupMatches = nextRound.setupMatches!!.filterNotNull().sortedBy { it.weighting }


            // todo Check CanCreateNewRound


            // --- Collect current round data

            val currentRoundTeams = !CompetitionMatchTeamRepo
                .get(currentRound.matches!!.map { it!!.competitionSetupMatch })
                .orDie()

            val currentRoundOutcomes =
                getSeedingList(currentRoundSetupMatches, nextRoundSetupMatches.sumOf { it.teams!! })
            logger.info { "Current round outcomes: $currentRoundOutcomes" }

            val currentTeamsSortedByMatches =
                currentRoundSetupMatches.map { match ->
                    currentRoundTeams.filter { it.competitionMatch == match.id }.sortedBy { it.place }
                }

            logger.info { "currentTeamsSortedByMatches: $currentTeamsSortedByMatches" }

            val currentTeamsWithOutcome = currentRoundOutcomes
                .mapIndexed { outcomesIndex, outcomes ->
                    currentTeamsSortedByMatches[outcomesIndex]
                        .mapIndexed { teamIndex, team -> team to outcomes[teamIndex] }
                }.flatten()

            logger.info { "Current Teams with Outcomes: $currentTeamsWithOutcome" }

            // --- Create next round

            val nextRoundSetupParticipants =
                !CompetitionSetupParticipantRepo.get(nextRoundSetupMatches.map { it.id }).orDie()
            logger.info { "nextRoundSetupParticipants: $nextRoundSetupParticipants" }

            val matchRecords = nextRoundSetupMatches
                .filterIndexed { index, _ -> index < currentRoundTeams.size }
                .map {
                    !it.applyCompetitionMatch(userId, null)
                }
            !CompetitionMatchRepo.create(matchRecords).orDie()

            val oldTeamToParticipantId = currentTeamsWithOutcome.map { cTeam ->
                cTeam.first to nextRoundSetupParticipants.find { p -> p.seed == cTeam.second } // Match Outcomes with ParticipantSeeds
            }.filter { team -> team.second != null } // Filter teams that have not made it to next round
            logger.info { "Old team to new participantSeed: $oldTeamToParticipantId" }


            val newTeamRecords = oldTeamToParticipantId.map { oldTeam ->

                val match = oldTeam.second!!.competitionSetupMatch!!

                CompetitionMatchTeamRecord(
                    id = UUID.randomUUID(),
                    competitionMatch = match,
                    competitionRegistration = oldTeam.first.competitionRegistration,
                    place = null,
                    createdAt = LocalDateTime.now(),
                    createdBy = userId,
                    updatedAt = LocalDateTime.now(),
                    updatedBy = userId,
                )
            }
            !CompetitionMatchTeamRepo.create(newTeamRecords).orDie()

            logger.info { "New Team Records: $newTeamRecords" }
        }


        noData
    }

    private fun getSeedingList(
        currentRoundSetupMatches: List<CompetitionSetupMatchRecord>,
        maxTeamsNeeded: Int
    ): List<List<Int>> {
        val currentRoundHighestTeamCount =
            getHighestTeamCount(currentRoundSetupMatches.map { it.teams }, maxTeamsNeeded)

        val currentRoundSeedings = currentRoundSetupMatches.map { mutableListOf<Int>() }

        var seedingsTaken = 0
        for (i in 0..<currentRoundHighestTeamCount) {
            fun addToList(index: Int) {
                if ((currentRoundSetupMatches[index].teams
                        ?: 0) > currentRoundSeedings[index].size || (currentRoundSetupMatches[index].teams == null && seedingsTaken < maxTeamsNeeded)
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