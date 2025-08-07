package de.lambda9.ready2race.backend.app.participant.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationNamedParticipantRepo
import de.lambda9.ready2race.backend.app.participant.control.*
import de.lambda9.ready2race.backend.app.participant.entity.*
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*

object ParticipantService {

    fun addParticipant(
        request: ParticipantUpsertDto,
        userId: UUID,
        clubId: UUID,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        val record = !request.toRecord(userId, clubId)
        val participantId = !ParticipantRepo.create(record).orDie()

        KIO.ok(ApiResponse.Created(participantId))
    }

    fun page(
        params: PaginationParameters<ParticipantSort>,
        clubId: UUID? = null,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): App<Nothing, ApiResponse.Page<ParticipantDto, ParticipantSort>> = KIO.comprehension {
        val total = !ParticipantRepo.count(params.search, clubId, user, scope).orDie()
        val page = !ParticipantRepo.page(params, clubId, user, scope).orDie()

        page.traverse { it.participantDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun pageForEvent(
        params: PaginationParameters<ParticipantForEventSort>,
        eventId: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): App<Nothing, ApiResponse.Page<ParticipantForEventDto, ParticipantForEventSort>> = KIO.comprehension {
        val total = !ParticipantForEventRepo.count(params.search, eventId, user, scope).orDie()
        val page = !ParticipantForEventRepo.page(params, eventId, user, scope).orDie()

        KIO.ok(
            ApiResponse.Page(
                data = page,
                pagination = params.toPagination(total)
            )
        )
    }

    fun getParticipant(
        id: UUID,
        clubId: UUID? = null,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): App<ParticipantError, ApiResponse.Dto<ParticipantDto>> = KIO.comprehension {
        val participant =
            !ParticipantRepo.getParticipant(id, clubId, user, scope).orDie()
                .onNullFail { ParticipantError.ParticipantNotFound }
        participant.participantDto().map { ApiResponse.Dto(it) }
    }

    fun updateParticipant(
        request: ParticipantUpsertDto,
        userId: UUID,
        clubId: UUID? = null,
        participantId: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): App<ParticipantError, ApiResponse.NoData> =
        ParticipantRepo.update(participantId, clubId, user, scope) {
            firstname = request.firstname
            lastname = request.lastname
            year = request.year
            gender = request.gender
            phone = request.phone
            external = request.external
            externalClubName = request.externalClubName?.trim()?.takeIf { it.isNotBlank() }
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()
            .onNullFail { ParticipantError.ParticipantNotFound }
            .map { ApiResponse.NoData }

    fun deleteParticipant(
        id: UUID,
        clubId: UUID? = null,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): App<ParticipantError, ApiResponse.NoData> = KIO.comprehension {

        !CompetitionRegistrationNamedParticipantRepo.existsByParticipantId(id)
            .orDie()
            .onTrueFail {
                ParticipantError.ParticipantInUse
            }

        val deleted = !ParticipantRepo.delete(id, clubId, user, scope).orDie()

        if (deleted < 1) {
            KIO.fail(ParticipantError.ParticipantNotFound)
        } else {
            noData
        }
    }

}