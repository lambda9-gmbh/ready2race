package de.lambda9.ready2race.backend.app.competitionRegistration.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesHasFeeRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationNamedParticipantRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationOptionalFeeRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationError
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationSort
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationTeamDto
import de.lambda9.ready2race.backend.app.eventRegistration.control.EventRegistrationRepo
import de.lambda9.ready2race.backend.app.eventRegistration.entity.CompetitionRegistrationTeamUpsertDto
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationOptionalFeeRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.ready2race.backend.lexiNumberComp
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*

object CompetitionRegistrationService {

    fun getByCompetition(
        params: PaginationParameters<CompetitionRegistrationSort>,
        competitionId: UUID,
        scope: Privilege.Scope,
        user: AppUserWithPrivilegesRecord,
    ): App<ServiceError, ApiResponse.Page<CompetitionRegistrationTeamDto, CompetitionRegistrationSort>> =
        KIO.comprehension {

            // TODO add search?
            val total = !CompetitionRegistrationRepo.countForCompetition(competitionId, scope, user).orDie()
            val page = !CompetitionRegistrationRepo.pageForCompetition(competitionId, params, scope, user).orDie()

            KIO.ok(
                ApiResponse.Page(
                    data = page,
                    pagination = params.toPagination(total)
                )
            )
        }

    fun create(
        request: CompetitionRegistrationTeamUpsertDto,
        eventId: UUID,
        competitionId: UUID,
        scope: Privilege.Scope,
        user: AppUserWithPrivilegesRecord,
    ): App<CompetitionRegistrationError, ApiResponse.Created> = KIO.comprehension {

        if (scope == Privilege.Scope.OWN) {
            !CompetitionRepo.isOpenForRegistration(competitionId, LocalDateTime.now()).orDie()
                .onFalseFail { CompetitionRegistrationError.RegistrationClosed }

            if (user.club != request.clubId) {
                return@comprehension KIO.fail(CompetitionRegistrationError.NotFound)
            }
        }

        val registrationId = !EventRegistrationRepo.findByEventAndClub(eventId, request.clubId!!).map { it?.id }.orDie()
            .onNullFail { CompetitionRegistrationError.EventRegistrationNotFound }

        val existingCount = !CompetitionRegistrationRepo.countForCompetitionAndClub(competitionId, request.clubId).orDie()

        val now = LocalDateTime.now()

        val competitionRegistrationId = !CompetitionRegistrationRepo.create(
            CompetitionRegistrationRecord(
                UUID.randomUUID(),
                registrationId,
                competitionId,
                request.clubId,
                "#${existingCount + 1}",
                now,
                user.id,
                now,
                user.id
            )
        ).orDie()

        request.namedParticipants.forEach { namedParticipantDto ->

            // TODO validate consistency

            !namedParticipantDto.participantIds.traverse { participantId ->

                !ParticipantRepo.existsByIdAndClub(participantId, request.clubId).orDie()
                    .failIf({ !it }) { CompetitionRegistrationError.RegistrationInvalid }

                CompetitionRegistrationNamedParticipantRepo.create(
                    CompetitionRegistrationNamedParticipantRecord(
                        competitionRegistrationId,
                        namedParticipantDto.namedParticipantId,
                        participantId
                    )
                )
            }.orDie()

        }

        request.optionalFees?.traverse {

            !CompetitionPropertiesHasFeeRepo.existsByCompetitionIdAndFeeId(competitionId, it).orDie()
                .failIf({ !it }) { CompetitionRegistrationError.RegistrationInvalid }

            CompetitionRegistrationOptionalFeeRepo.create(
                CompetitionRegistrationOptionalFeeRecord(
                    competitionRegistrationId,
                    it
                )
            )
        }?.orDie()?.not()

        KIO.ok(ApiResponse.Created(competitionRegistrationId))
    }

    fun update(
        request: CompetitionRegistrationTeamUpsertDto,
        competitionId: UUID,
        competitionRegistrationId: UUID,
        scope: Privilege.Scope,
        user: AppUserWithPrivilegesRecord,
    ): App<CompetitionRegistrationError, ApiResponse.NoData> = KIO.comprehension {

        if (scope == Privilege.Scope.OWN) {
            !CompetitionRepo.isOpenForRegistration(competitionId, LocalDateTime.now()).orDie()
                .onFalseFail { CompetitionRegistrationError.RegistrationClosed }

            if (user.club != request.clubId) {
                return@comprehension KIO.fail(CompetitionRegistrationError.NotFound)
            }

        }

        val registration =
            !CompetitionRegistrationRepo.findByIdAndCompetitionId(competitionRegistrationId, competitionId).orDie()
                .onNullFail { CompetitionRegistrationError.NotFound }

        !CompetitionRegistrationNamedParticipantRepo.deleteAllByRegistrationId(registration.id).orDie()

        request.namedParticipants.forEach { namedParticipantDto ->

            // TODO validate consistency

            !namedParticipantDto.participantIds.traverse { participantId ->

                !ParticipantRepo.existsByIdAndClub(participantId, registration.club).orDie()
                    .failIf({ !it }) { CompetitionRegistrationError.RegistrationInvalid }

                CompetitionRegistrationNamedParticipantRepo.create(
                    CompetitionRegistrationNamedParticipantRecord(
                        competitionRegistrationId,
                        namedParticipantDto.namedParticipantId,
                        participantId
                    )
                )
            }.orDie()

        }

        !CompetitionRegistrationOptionalFeeRepo.deleteAllByRegistrationId(registration.id).orDie()

        request.optionalFees?.traverse {

            !CompetitionPropertiesHasFeeRepo.existsByCompetitionIdAndFeeId(competitionId, it).orDie()
                .failIf({ !it }) { CompetitionRegistrationError.RegistrationInvalid }

            CompetitionRegistrationOptionalFeeRepo.create(
                CompetitionRegistrationOptionalFeeRecord(
                    competitionRegistrationId,
                    it
                )
            )
        }?.orDie()?.not()

        KIO.ok(ApiResponse.NoData)
    }


    fun delete(
        competitionId: UUID,
        competitionRegistrationId: UUID,
        scope: Privilege.Scope,
        user: AppUserWithPrivilegesRecord,
    ): App<CompetitionRegistrationError, ApiResponse.NoData> = KIO.comprehension {

        if (scope == Privilege.Scope.OWN) {
            !CompetitionRepo.isOpenForRegistration(competitionId, LocalDateTime.now()).orDie()
                .onFalseFail { CompetitionRegistrationError.RegistrationClosed }
        } else {
            // TODO check no race exists yet
        }

        val clubId = !CompetitionRegistrationRepo.getClub(competitionRegistrationId).orDie()
            .onNullFail { CompetitionRegistrationError.NotFound }

        !CompetitionRegistrationRepo.delete(competitionRegistrationId, scope, user).orDie()
            .failIf({ it < 1 }) { CompetitionRegistrationError.NotFound }

        val remaining = !CompetitionRegistrationRepo.getByCompetitionAndClub(competitionId, clubId).orDie()

        remaining.sortedWith(lexiNumberComp { it.name }).mapIndexed { idx, rec ->
            rec.name = "#${idx + 1}"
            rec.update()
        }

        noData
    }

}