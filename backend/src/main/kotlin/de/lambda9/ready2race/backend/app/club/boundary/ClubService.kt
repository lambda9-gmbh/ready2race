package de.lambda9.ready2race.backend.app.club.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.club.control.ClubRepo
import de.lambda9.ready2race.backend.app.club.control.clubDto
import de.lambda9.ready2race.backend.app.club.control.toRecord
import de.lambda9.ready2race.backend.app.club.entity.ClubDto
import de.lambda9.ready2race.backend.app.club.entity.ClubError
import de.lambda9.ready2race.backend.app.club.entity.ClubSort
import de.lambda9.ready2race.backend.app.club.entity.ClubUpsertDto
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object ClubService {

    fun addClub(
        request: ClubUpsertDto,
        userId: UUID,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        val record = !request.toRecord(userId)
        val clubId = !ClubRepo.create(record).orDie()

        KIO.ok(ApiResponse.Created(clubId))
    }

    fun page(
        params: PaginationParameters<ClubSort>,
    ): App<Nothing, ApiResponse.Page<ClubDto, ClubSort>> = KIO.comprehension {
        val total = !ClubRepo.count(params.search).orDie()
        val page = !ClubRepo.page(params).orDie()

        page.forEachM { it.clubDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getClub(
        id: UUID,
    ): App<ClubError, ApiResponse.Dto<ClubDto>> = KIO.comprehension {
        val club = !ClubRepo.getClub(id).orDie().onNullFail { ClubError.ClubNotFound }
        club.clubDto().map { ApiResponse.Dto(it) }
    }

    fun updateClub(
        request: ClubUpsertDto,
        userId: UUID,
        clubId: UUID,
    ): App<ClubError, ApiResponse.NoData> =
        ClubRepo.update(clubId) {
            name = request.name
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()
            .onNullFail { ClubError.ClubNotFound }
            .map { ApiResponse.NoData }

    fun deleteClub(
        id: UUID,
    ): App<ClubError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !ClubRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(ClubError.ClubNotFound)
        } else {
            noData
        }
    }

}