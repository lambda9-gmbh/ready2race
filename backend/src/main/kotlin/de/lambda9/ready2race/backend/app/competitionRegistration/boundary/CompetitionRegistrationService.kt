package de.lambda9.ready2race.backend.app.competitionRegistration.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesHasFeeRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesHasNamedParticipantRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationNamedParticipantRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationOptionalFeeRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationError
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationSort
import de.lambda9.ready2race.backend.app.competitionRegistration.entity.CompetitionRegistrationTeamDto
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.eventRegistration.control.EventRegistrationRepo
import de.lambda9.ready2race.backend.app.eventRegistration.entity.CompetitionRegistrationNamedParticipantUpsertDto
import de.lambda9.ready2race.backend.app.eventRegistration.entity.CompetitionRegistrationTeamUpsertDto
import de.lambda9.ready2race.backend.app.eventRegistration.entity.OpenForRegistrationType
import de.lambda9.ready2race.backend.app.invoice.entity.RegistrationInvoiceType
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationOptionalFeeRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.ready2race.backend.lexiNumberComp
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.ok
import de.lambda9.tailwind.core.KIO.Companion.unit
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

            ok(
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
        diffRegType: RegistrationInvoiceType?,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        !validateScope(scope, competitionId, user, request.clubId!!)

        val type = diffRegType.takeIf { scope == Privilege.Scope.GLOBAL }
            ?: !EventService.getOpenForRegistrationType(eventId).map {
                when (it) {
                    OpenForRegistrationType.REGULAR -> RegistrationInvoiceType.REGULAR
                    OpenForRegistrationType.LATE -> RegistrationInvoiceType.LATE
                    OpenForRegistrationType.CLOSED -> null
                }
            }.onNullDie("Already validated: Either global permission with specified type or failed on own permission when closed")

        val isLate = type == RegistrationInvoiceType.LATE

        val registrationId = !EventRegistrationRepo.findByEventAndClub(eventId, request.clubId).map { it?.id }.orDie()
            .onNullFail { CompetitionRegistrationError.EventRegistrationNotFound }

        val existingCount =
            !CompetitionRegistrationRepo.countForCompetitionAndClub(competitionId, request.clubId).orDie()

        val name = when {

            existingCount < 1 -> {
                null
            }

            existingCount == 1 -> {
                val first = !CompetitionRegistrationRepo.getByCompetitionAndClub(competitionId, request.clubId).orDie().map { it.singleOrNull() }.onNullDie("Count returned 1 row, select returned NOT 1 row.")
                first.name = "#1"
                first.update()
                "#2"
            }

            else -> "#${existingCount + 1}"
        }

        val now = LocalDateTime.now()

        val competitionRegistrationId = !CompetitionRegistrationRepo.create(
            CompetitionRegistrationRecord(
                UUID.randomUUID(),
                registrationId,
                competitionId,
                request.clubId,
                name,
                now,
                user.id,
                now,
                user.id,
                isLate = isLate,
            )
        ).orDie()

        !request.namedParticipants.traverse { namedParticipantDto ->
            insertNamedParticipants(competitionId, namedParticipantDto, competitionRegistrationId, request.clubId)
        }

        request.optionalFees?.traverse {
            insertOptionalFees(competitionId, it, competitionRegistrationId)
        }?.not()

        ok(ApiResponse.Created(competitionRegistrationId))
    }

    fun update(
        request: CompetitionRegistrationTeamUpsertDto,
        eventId: UUID,
        competitionId: UUID,
        competitionRegistrationId: UUID,
        scope: Privilege.Scope,
        user: AppUserWithPrivilegesRecord,
        diffRegType: RegistrationInvoiceType?,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !validateScope(scope, competitionId, user, request.clubId!!)

        val registration =
            !CompetitionRegistrationRepo.findByIdAndCompetitionId(competitionRegistrationId, competitionId).orDie()
                .onNullFail { CompetitionRegistrationError.NotFound }

        if (scope == Privilege.Scope.GLOBAL) {
            registration.isLate = diffRegType == RegistrationInvoiceType.LATE
            registration.update()
        } else {
            val type = !EventService.getOpenForRegistrationType(eventId)
            val changeIsLate = type == OpenForRegistrationType.LATE
            !KIO.failOn(registration.isLate != changeIsLate ) { CompetitionRegistrationError.RegistrationClosed }
        }

        !CompetitionRegistrationNamedParticipantRepo.deleteAllByRegistrationId(registration.id).orDie()

        !request.namedParticipants.traverse { namedParticipantDto ->
            insertNamedParticipants(competitionId, namedParticipantDto, competitionRegistrationId, request.clubId)
        }

        !CompetitionRegistrationOptionalFeeRepo.deleteAllByRegistrationId(registration.id).orDie()

        request.optionalFees?.traverse {
            insertOptionalFees(competitionId, it, competitionRegistrationId)
        }?.not()

        ok(ApiResponse.NoData)
    }

    private fun validateScope(
        scope: Privilege.Scope,
        competitionId: UUID,
        user: AppUserWithPrivilegesRecord,
        clubId: UUID
    ) = KIO.comprehension {
        if (scope == Privilege.Scope.OWN) {
            !CompetitionRepo.isOpenForRegistration(competitionId, LocalDateTime.now()).orDie()
                .onFalseFail { CompetitionRegistrationError.RegistrationClosed }

            if (user.club != clubId) {
                KIO.fail(CompetitionRegistrationError.NotFound)
            }
        }
        unit
    }

    private fun insertNamedParticipants(
        competitionId: UUID,
        namedParticipantDto: CompetitionRegistrationNamedParticipantUpsertDto,
        competitionRegistrationId: UUID,
        clubId: UUID
    ) = KIO.comprehension {

        val requirements =
            !CompetitionPropertiesHasNamedParticipantRepo.getByCompetitionAndNamedParticipantId(competitionId, namedParticipantDto.namedParticipantId)
                .orDie()
                .onNullFail { CompetitionRegistrationError.RegistrationInvalid }
        val counts: MutableMap<Gender, Int> = mutableMapOf(
            Gender.M to 0,
            Gender.F to 0,
            Gender.D to 0,
        )
        !namedParticipantDto.participantIds.traverse { participantId ->

            KIO.comprehension {

                val participant = !ParticipantRepo.findByIdAndClub(participantId, clubId)
                    .orDie()
                    .onNullFail { CompetitionRegistrationError.RegistrationInvalid }

                !CompetitionRegistrationNamedParticipantRepo.existsByParticipantIdAndCompetitionId(
                    participantId,
                    competitionId
                )
                    .orDie()
                    .onTrueFail {
                        CompetitionRegistrationError.DuplicateParticipant
                    }

                counts[participant.gender] = (counts[participant.gender] ?: 0) + 1

                CompetitionRegistrationNamedParticipantRepo.create(
                    CompetitionRegistrationNamedParticipantRecord(
                        competitionRegistrationId,
                        namedParticipantDto.namedParticipantId,
                        participantId
                    )
                ).orDie()
            }
        }

        if (requirements.countMales > counts[Gender.M]!!
            || requirements.countFemales > counts[Gender.F]!!
            || requirements.countNonBinary > counts[Gender.D]!!
            || (requirements.countMixed
                + requirements.countMales
                + requirements.countFemales
                + requirements.countNonBinary
                ) != counts.values.sum()
        ) {
            KIO.fail(CompetitionRegistrationError.RegistrationInvalid)
        } else {
            unit
        }
    }

    private fun insertOptionalFees(
        competitionId: UUID,
        feeId: UUID,
        competitionRegistrationId: UUID
    ) = KIO.comprehension {
        !CompetitionPropertiesHasFeeRepo.existsByCompetitionIdAndFeeId(competitionId, feeId).orDie()
            .onFalseFail { CompetitionRegistrationError.RegistrationInvalid }

        CompetitionRegistrationOptionalFeeRepo.create(
            CompetitionRegistrationOptionalFeeRecord(
                competitionRegistrationId,
                feeId
            )
        ).orDie()
    }

    fun delete(
        eventId: UUID,
        competitionId: UUID,
        competitionRegistrationId: UUID,
        scope: Privilege.Scope,
        user: AppUserWithPrivilegesRecord,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val registration = !CompetitionRegistrationRepo.findByIdAndCompetitionId(competitionRegistrationId, competitionId).orDie()
            .onNullFail { CompetitionRegistrationError.NotFound }

        !validateScope(scope, competitionId, user, registration.club)

        if (scope == Privilege.Scope.OWN) {
            val type = !EventService.getOpenForRegistrationType(eventId)
            val changeIsLate = type == OpenForRegistrationType.LATE
            !KIO.failOn(registration.isLate != changeIsLate ) { CompetitionRegistrationError.RegistrationClosed }
        } else {
            // TODO check no race exists yet
        }

        val remaining = !CompetitionRegistrationRepo.getByCompetitionAndClub(competitionId, registration.club).orDie()

        if (remaining.size == 1) {
            remaining.first().let {
                it.name = null
                it.update()
            }
        } else {
            remaining.sortedWith(lexiNumberComp { it.name }).mapIndexed { idx, rec ->
                rec.name = "#${idx + 1}"
                rec.update()
            }
        }

        registration.delete()

        noData
    }

}