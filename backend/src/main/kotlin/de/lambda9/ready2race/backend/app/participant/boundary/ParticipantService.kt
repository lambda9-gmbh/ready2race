package de.lambda9.ready2race.backend.app.participant.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.club.entity.ParticipantDto
import de.lambda9.ready2race.backend.app.club.entity.ParticipantUpsertDto
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.participant.control.participantDto
import de.lambda9.ready2race.backend.app.participant.control.toRecord
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantError
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
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

        page.forEachM { it.participantDto() }.map {
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