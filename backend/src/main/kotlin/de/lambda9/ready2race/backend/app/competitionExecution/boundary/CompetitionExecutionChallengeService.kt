package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionError
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamDocumentDataRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamDocumentRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionChallengeResultRequest
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionChallengeError
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionChallengeError.SelfSubmissionNotAllowed
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionExecutionError
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationError
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupMatchRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupRoundRepo
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventParticipant.control.EventParticipantRepo
import de.lambda9.ready2race.backend.app.eventParticipant.entity.EventParticipantError
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object CompetitionExecutionChallengeService {

    fun createChallengeSetup(
        competitionProperties: CompetitionPropertiesRecord,
        userId: UUID,
    ): App<ServiceError, Unit> = KIO.comprehension {

        val now = LocalDateTime.now()

        val roundSetupRecord = CompetitionSetupRoundRecord(
            id = UUID.randomUUID(),
            competitionSetup = competitionProperties.id,
            competitionSetupTemplate = null,
            nextRound = null,
            name = competitionProperties.name,
            required = true,
            useDefaultSeeding = true,
            placesOption = CompetitionSetupPlacesOption.ASCENDING.name
        )
        !CompetitionSetupRoundRepo.create(listOf(roundSetupRecord)).orDie()

        val setupMatchRecord = CompetitionSetupMatchRecord(
            id = UUID.randomUUID(),
            competitionSetupRound = roundSetupRecord.id,
            competitionSetupGroup = null,
            weighting = 1,
            teams = null,
            name = competitionProperties.name,
            executionOrder = 1,
            startTimeOffset = null,
        )
        !CompetitionSetupMatchRepo.create(listOf(setupMatchRecord)).orDie()

        val matchRecord = CompetitionMatchRecord(
            competitionSetupMatch = setupMatchRecord.id,
            startTime = null,
            createdAt = now,
            createdBy = userId,
            updatedAt = now,
            updatedBy = userId,
        )
        !CompetitionMatchRepo.create(listOf(matchRecord)).orDie()

        KIO.unit
    }


    fun saveChallengeResult(
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
        competitionId: UUID,
        competitionRegistrationId: UUID,
        request: CompetitionChallengeResultRequest,
        file: File?,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val userId = user.id!!

        val competition = !CompetitionRepo.getById(competitionId).orDie()
            .onNullFail { CompetitionError.CompetitionNotFound }


        val now = LocalDateTime.now()

        val event = !EventRepo.get(competition.event!!).orDie()
            .onNullFail { EventError.NotFound }
        !KIO.failOn(event.challengeEvent != true) { CompetitionExecutionChallengeError.NotAChallengeEvent }
        !KIO.failOn(scope == Privilege.Scope.OWN && event.selfSubmission != true) { SelfSubmissionNotAllowed }

        !KIO.failOn(competition.challengeResultConfirmationImageRequired == true && file == null) { CompetitionExecutionError.ResultConfirmationImageMissing }

        // Submitting results outside the timespan is only permitted for the GLOBAL Scope
        val outsideChallengeTimespan =
            competition.challengeStartAt?.isAfter(now) ?: false || competition.challengeEndAt?.isBefore(now) ?: false
        !KIO.failOn(scope == Privilege.Scope.OWN && outsideChallengeTimespan) { CompetitionExecutionError.NotInChallengeTimespan }

        val round = !CompetitionSetupRoundRepo.getWithMatchesBySetup(competition.propertiesId!!).orDie()
            .andThen {
                !KIO.failOn(it.isEmpty()) { CompetitionExecutionChallengeError.ChallengeNotStartedYet }
                !KIO.failOn(it.size > 1) { CompetitionExecutionChallengeError.CorruptedSetup }
                !KIO.failOn(
                    (it.first().setupMatches?.size ?: 0) != 1
                ) { CompetitionExecutionChallengeError.CorruptedSetup }

                KIO.ok(it)
            }.map { it.first() }

        val match = round.matches!!.first()!!


        val competitionRegistrationRecord =
            !CompetitionRegistrationRepo.findByIdAndCompetitionId(competitionRegistrationId, competitionId).orDie()
                .onNullFail { CompetitionRegistrationError.NotFound }

        // Check if user is allowed to submit results
        !KIO.failOn(scope == Privilege.Scope.OWN && (competitionRegistrationRecord.club != user.club)) { CompetitionRegistrationError.NotFound }

        // Results can only be submitted once // todo: allow this for admins? #17913
        !CompetitionMatchTeamRepo.existsByMatchAndRegistrationId(
            matchId = match.competitionSetupMatch!!,
            registrationId = competitionRegistrationId
        ).orDie()
            .onTrueFail { CompetitionExecutionChallengeError.ResultAlreadySubmitted }


        // Create Team including the results

        val highestTeamNumber = !CompetitionRegistrationRepo.getHighestTeamNumber(competitionId).orDie()

        !CompetitionRegistrationRepo.update(competitionRegistrationRecord) {
            competitionRegistrationRecord.teamNumber = highestTeamNumber?.let { it + 1 } ?: 1
            competitionRegistrationRecord.updatedBy = userId
            competitionRegistrationRecord.updatedAt = LocalDateTime.now()
        }.orDie()

        val highestStartNumber = !CompetitionMatchTeamRepo.getHighestStartNumber(match.competitionSetupMatch!!).orDie()

        val competitionMatchTeamRecord = CompetitionMatchTeamRecord(
            id = UUID.randomUUID(),
            competitionMatch = match.competitionSetupMatch!!,
            competitionRegistration = competitionRegistrationRecord.id,
            startNumber = highestStartNumber?.let { it + 1 } ?: 1,
            place = null,
            resultValue = request.result,
            createdAt = now,
            createdBy = userId,
            updatedAt = now,
            updatedBy = userId,
        )
        !CompetitionMatchTeamRepo.create(listOf(competitionMatchTeamRecord)).orDie()


        // save documents
        if (file != null) {
            val documentId = !CompetitionMatchTeamDocumentRepo.create(
                CompetitionMatchTeamDocumentRecord(
                    id = UUID.randomUUID(),
                    competitionMatchTeamId = competitionMatchTeamRecord.id,
                    name = file.name,
                    createdAt = now,
                    createdBy = userId,
                    updatedAt = now,
                    updatedBy = userId,
                )
            ).orDie()

            !CompetitionMatchTeamDocumentDataRepo.create(
                CompetitionMatchTeamDocumentDataRecord(
                    competitionMatchTeamDocumentId = documentId,
                    data = file.bytes,
                )
            ).orDie()
        }


        ApiResponse.noData
    }

    fun saveChallengeResult(
        accessToken: String,
        competitionId: UUID,
        competitionRegistrationId: UUID,
        request: CompetitionChallengeResultRequest,
        file: File?,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val eventParticipant = !EventParticipantRepo.getByToken(accessToken).orDie().onNullFail { EventParticipantError.TokenNotFound }

        val participant = !ParticipantRepo.get(eventParticipant.participant).orDie().onNullDie("Referenced entity")

        val competition = !CompetitionRepo.getById(competitionId).orDie()
            .onNullFail { CompetitionError.CompetitionNotFound }

        val now = LocalDateTime.now()

        val event = !EventRepo.get(competition.event!!).orDie()
            .onNullFail { EventError.NotFound }
        !KIO.failOn(event.challengeEvent != true) { CompetitionExecutionChallengeError.NotAChallengeEvent }
        !KIO.failOn(event.selfSubmission != true) { SelfSubmissionNotAllowed }

        !KIO.failOn(competition.challengeResultConfirmationImageRequired == true && file == null) { CompetitionExecutionError.ResultConfirmationImageMissing }

        // Submitting results outside the timespan is only permitted for the GLOBAL Scope
        val outsideChallengeTimespan =
            competition.challengeStartAt?.isAfter(now) ?: false || competition.challengeEndAt?.isBefore(now) ?: false
        !KIO.failOn(outsideChallengeTimespan) { CompetitionExecutionError.NotInChallengeTimespan }

        val round = !CompetitionSetupRoundRepo.getWithMatchesBySetup(competition.propertiesId!!).orDie()
            .andThen {
                !KIO.failOn(it.isEmpty()) { CompetitionExecutionChallengeError.ChallengeNotStartedYet }
                !KIO.failOn(it.size > 1) { CompetitionExecutionChallengeError.CorruptedSetup }
                !KIO.failOn(
                    (it.first().setupMatches?.size ?: 0) != 1
                ) { CompetitionExecutionChallengeError.CorruptedSetup }

                KIO.ok(it)
            }.map { it.first() }

        val match = round.matches!!.first()!!


        val competitionRegistrationRecord =
            !CompetitionRegistrationRepo.findByIdAndCompetitionId(competitionRegistrationId, competitionId).orDie()
                .onNullFail { CompetitionRegistrationError.NotFound }

        // Check if user is allowed to submit results
        !KIO.failOn(competitionRegistrationRecord.club != participant.club) { CompetitionRegistrationError.NotFound }
        // TODO: @Improve validation "from team including this participant"

        // Results can only be submitted once // todo: allow this for admins? #17913
        !CompetitionMatchTeamRepo.existsByMatchAndRegistrationId(
            matchId = match.competitionSetupMatch!!,
            registrationId = competitionRegistrationId
        ).orDie()
            .onTrueFail { CompetitionExecutionChallengeError.ResultAlreadySubmitted }


        // Create Team including the results

        val highestTeamNumber = !CompetitionRegistrationRepo.getHighestTeamNumber(competitionId).orDie()

        !CompetitionRegistrationRepo.update(competitionRegistrationRecord) {
            competitionRegistrationRecord.teamNumber = highestTeamNumber?.let { it + 1 } ?: 1
            competitionRegistrationRecord.updatedBy = null
            competitionRegistrationRecord.updatedAt = LocalDateTime.now()
        }.orDie()

        val highestStartNumber = !CompetitionMatchTeamRepo.getHighestStartNumber(match.competitionSetupMatch!!).orDie()

        val competitionMatchTeamRecord = CompetitionMatchTeamRecord(
            id = UUID.randomUUID(),
            competitionMatch = match.competitionSetupMatch!!,
            competitionRegistration = competitionRegistrationRecord.id,
            startNumber = highestStartNumber?.let { it + 1 } ?: 1,
            place = null,
            resultValue = request.result,
            createdAt = now,
            createdBy = null,
            updatedAt = now,
            updatedBy = null,
        )
        !CompetitionMatchTeamRepo.create(listOf(competitionMatchTeamRecord)).orDie()


        // save documents
        if (file != null) {
            val documentId = !CompetitionMatchTeamDocumentRepo.create(
                CompetitionMatchTeamDocumentRecord(
                    id = UUID.randomUUID(),
                    competitionMatchTeamId = competitionMatchTeamRecord.id,
                    name = file.name,
                    createdAt = now,
                    createdBy = null,
                    updatedAt = now,
                    updatedBy = null,
                )
            ).orDie()

            !CompetitionMatchTeamDocumentDataRepo.create(
                CompetitionMatchTeamDocumentDataRecord(
                    competitionMatchTeamDocumentId = documentId,
                    data = file.bytes,
                )
            ).orDie()
        }


        ApiResponse.noData
    }
}