package de.lambda9.ready2race.backend.app.participant.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.club.entity.ParticipantDto
import de.lambda9.ready2race.backend.app.club.entity.ParticipantForEventDto
import de.lambda9.ready2race.backend.app.club.entity.ParticipantUpsertDto
import de.lambda9.ready2race.backend.app.participant.control.*
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantError
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventSort
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
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
    ): App<Nothing, ApiResponse.Page<ParticipantDto, ParticipantSort>> = KIO.comprehension {
        val total = !ParticipantRepo.count(params.search, clubId).orDie()
        val page = !ParticipantRepo.page(params, clubId).orDie()

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
    ): App<Nothing, ApiResponse.Page<ParticipantForEventDto, ParticipantForEventSort>> = KIO.comprehension {
        val total = !ParticipantForEventRepo.count(params.search, eventId).orDie()
        val page = !ParticipantForEventRepo.page(params, eventId).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getParticipant(
        id: UUID,
        clubId: UUID? = null,
    ): App<ParticipantError, ApiResponse.Dto<ParticipantDto>> = KIO.comprehension {
        val participant =
            !ParticipantRepo.getParticipant(id, clubId).orDie().onNullFail { ParticipantError.ParticipantNotFound }
        participant.participantDto().map { ApiResponse.Dto(it) }
    }

    fun updateParticipant(
        request: ParticipantUpsertDto,
        userId: UUID,
        clubId: UUID? = null,
        participantId: UUID,
    ): App<ParticipantError, ApiResponse.NoData> =
        ParticipantRepo.update(participantId, clubId) {
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
    ): App<ParticipantError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !ParticipantRepo.delete(id, clubId).orDie()

        if (deleted < 1) {
            KIO.fail(ParticipantError.ParticipantNotFound)
        } else {
            noData
        }
    }

}