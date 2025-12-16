package de.lambda9.ready2race.backend.app.competitionDeregistration.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionError
import de.lambda9.ready2race.backend.app.competitionDeregistration.control.CompetitionDeregistrationRepo
import de.lambda9.ready2race.backend.app.competitionDeregistration.control.toRecord
import de.lambda9.ready2race.backend.app.competitionDeregistration.entity.CompetitionDeregistrationError
import de.lambda9.ready2race.backend.app.competitionDeregistration.entity.CompetitionDeregistrationRequest
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService.getCurrentAndNextRound
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationError
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.eventRegistration.entity.OpenForRegistrationType
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object CompetitionDeregistrationService {

    fun createCompetitionDeregistration(
        userId: UUID,
        competitionId: UUID,
        competitionRegistrationId: UUID,
        request: CompetitionDeregistrationRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !CompetitionRegistrationRepo.exists(competitionRegistrationId).orDie()
            .onFalseFail { CompetitionRegistrationError.NotFound }

        !CompetitionDeregistrationRepo.exists(competitionRegistrationId).orDie().onTrueFail {
            CompetitionDeregistrationError.NotFound
        }

        val rounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
        val (currentRound, _) = getCurrentAndNextRound(rounds)

        val match = currentRound?.matches
            ?.find { match -> match.teams.any { it.competitionRegistration == competitionRegistrationId } }

        // Team is not present in current round (if the first round is already created)
        !KIO.failOn(currentRound != null && match == null) {
            CompetitionDeregistrationError.NotInCurrentRound
        }

        // Results already exist in current match of this team
        !KIO.failOn(match?.teams?.any { it.place != null || !it.failed } ?: false) {
            CompetitionDeregistrationError.ResultsAlreadyExists
        }

        // If registration is still open
        val competition = !CompetitionRepo.getById(competitionId).orDie()
            .onNullFail { CompetitionError.CompetitionNotFound }
        val openForRegistrationType = !EventService.getOpenForRegistrationType(competition.event!!)
        !KIO.failOn(openForRegistrationType != OpenForRegistrationType.CLOSED) {
            CompetitionDeregistrationError.RegistrationStillOpen
        }

        val record = !request.toRecord(userId, competitionRegistrationId, currentRound?.setupRoundId)
        !CompetitionDeregistrationRepo.create(record).orDie()
        noData
    }

    fun removeCompetitionDeregistration(
        competitionId: UUID,
        competitionRegistrationId: UUID
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val deregistration = !CompetitionDeregistrationRepo.get(competitionRegistrationId).orDie()
            .onNullFail { CompetitionDeregistrationError.NotFound }

        val rounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
        val (currentRound, _) = getCurrentAndNextRound(rounds)

        if (currentRound != null) {
            val sortedRounds = CompetitionExecutionService.sortRounds(rounds).map { it.setupRoundId }
            val previousRounds = sortedRounds
                .filterIndexed { index, _ -> index < sortedRounds.indexOf(currentRound.setupRoundId) }

            // Fail if there was a new round created since the creation of this deregistration
            !KIO.failOn(deregistration.competitionSetupRound == null || previousRounds.contains(deregistration.competitionSetupRound)) {
                CompetitionDeregistrationError.IsLocked
            }
        }

        !CompetitionDeregistrationRepo.delete(competitionRegistrationId).orDie()

        noData
    }

}