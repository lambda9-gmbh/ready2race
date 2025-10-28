package de.lambda9.ready2race.backend.app.competitionDeregistration.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionDeregistration.control.CompetitionDeregistrationRepo
import de.lambda9.ready2race.backend.app.competitionDeregistration.control.toRecord
import de.lambda9.ready2race.backend.app.competitionDeregistration.entity.CompetitionDeregistrationError
import de.lambda9.ready2race.backend.app.competitionDeregistration.entity.CompetitionDeregistrationRequest
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService.getCurrentAndNextRound
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationError
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.UUID

object CompetitionDeregistrationService {

    fun createCompetitionDeregistration(
        userId: UUID,
        competitionId: UUID,
        competitionRegistrationId: UUID,
        request: CompetitionDeregistrationRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !CompetitionRegistrationRepo.exists(competitionRegistrationId).orDie()
            .onFalseFail { CompetitionRegistrationError.NotFound }

        // todo: Dont allow this action if results are all set
        // todo: ??? fail if registration is still open?

        val rounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
        val (currentRound, _) = getCurrentAndNextRound(rounds)

        // Remove a potential place from the Registered Team - This is to prevent duplicate keys if the place is left unattended
        val matchTeam = currentRound?.matches?.flatMap { match -> match.teams }
            ?.find { it.competitionRegistration == competitionRegistrationId }

        if (matchTeam != null) {
            !CompetitionMatchTeamRepo.updateByMatchAndRegistrationId(
                matchTeam.competitionMatch,
                competitionRegistrationId
            ) {
                place = null
                updatedBy = userId
                updatedAt = LocalDateTime.now()
            }.orDie().onNullFail { CompetitionExecutionError.MatchTeamNotFound }
        }

        !CompetitionDeregistrationRepo.exists(competitionRegistrationId).orDie().onTrueFail {
            CompetitionDeregistrationError.NotFound
        }

        val record = !request.toRecord(userId, competitionRegistrationId, currentRound?.setupRoundId)
        !CompetitionDeregistrationRepo.create(record).orDie()
        noData
    }

    fun removeCompetitionDeregistration(
        competitionId: UUID,
        competitionRegistrationId: UUID
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !CompetitionRegistrationRepo.exists(competitionRegistrationId).orDie()
            .onFalseFail { CompetitionRegistrationError.NotFound }

        // Fail if there was a new round created since the creation of this deregistration
        val rounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
        val (currentRound, _) = getCurrentAndNextRound(rounds)
        val deregistration = !CompetitionDeregistrationRepo.get(competitionRegistrationId).orDie()
        !KIO.failOn(deregistration != null && deregistration.competitionSetupRound != currentRound?.setupRoundId) { CompetitionDeregistrationError.IsLocked }

        val deleted = !CompetitionDeregistrationRepo.delete(competitionRegistrationId).orDie()

        if (deleted < 1) {
            KIO.fail(CompetitionDeregistrationError.NotFound)
        } else {
            noData
        }
    }

}