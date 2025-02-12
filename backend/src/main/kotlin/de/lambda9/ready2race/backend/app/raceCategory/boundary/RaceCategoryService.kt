package de.lambda9.ready2race.backend.app.raceCategory.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.raceCategory.control.RaceCategoryRepo
import de.lambda9.ready2race.backend.app.raceCategory.control.raceCategoryDto
import de.lambda9.ready2race.backend.app.raceCategory.control.toRecord
import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategoryDto
import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategoryError
import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategoryRequest
import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategorySort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.UUID

object RaceCategoryService {

    fun addRaceCategory(
        request: RaceCategoryRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        val raceCategoryId = !RaceCategoryRepo.create(record).orDie()
        KIO.ok(ApiResponse.Created(raceCategoryId))
    }

    fun page(
        params: PaginationParameters<RaceCategorySort>
    ): App<Nothing, ApiResponse.Page<RaceCategoryDto, RaceCategorySort>> = KIO.comprehension {
        val total = !RaceCategoryRepo.count(params.search).orDie()
        val page = !RaceCategoryRepo.page(params).orDie()

        page.forEachM { it.raceCategoryDto() }.map{
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }


    fun updateRaceCategory(
        raceCategoryId: UUID,
        request: RaceCategoryRequest,
        userId: UUID,
    ): App<RaceCategoryError, ApiResponse.NoData> = KIO.comprehension {
        !RaceCategoryRepo.update(raceCategoryId) {
            name = request.name
            description = request.description
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie().onNullFail { RaceCategoryError.RaceCategoryNotFound }

        noData
    }

    fun deleteRaceCategory(
        raceCategoryId: UUID
    ): App<RaceCategoryError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !RaceCategoryRepo.delete(raceCategoryId).orDie()
        if(deleted < 1) {
            KIO.fail(RaceCategoryError.RaceCategoryNotFound)
        } else{
            noData
        }
    }

}