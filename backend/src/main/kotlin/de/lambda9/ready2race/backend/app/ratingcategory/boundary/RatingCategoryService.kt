package de.lambda9.ready2race.backend.app.ratingcategory.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ratingcategory.control.RatingCategoryRepo
import de.lambda9.ready2race.backend.app.ratingcategory.control.toDto
import de.lambda9.ready2race.backend.app.ratingcategory.control.toRecord
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryDto
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryError
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRequest
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategorySort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.responses.createdResponse
import de.lambda9.ready2race.backend.calls.responses.noDataResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.UUID

object RatingCategoryService {

    fun addCategory(
        request: RatingCategoryRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        RatingCategoryRepo.create(record).orDie().createdResponse()
    }

    fun page(
        params: PaginationParameters<RatingCategorySort>,
    ): App<Nothing, ApiResponse.Page<RatingCategoryDto, RatingCategorySort>> = KIO.comprehension {
        val total = !RatingCategoryRepo.count(params.search).orDie()
        val page = !RatingCategoryRepo.page(params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun updateCategory(
        id: UUID,
        request: RatingCategoryRequest,
        userId: UUID,
    ): App<RatingCategoryError, ApiResponse.NoData> =
        RatingCategoryRepo.update(id) {
            name = request.name
            description = request.description
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()
            .onNullFail { RatingCategoryError.NotFound }
            .noDataResponse()

    fun deleteCategory(
        id: UUID,
    ): App<RatingCategoryError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !RatingCategoryRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(RatingCategoryError.NotFound)
        } else {
            noData
        }
    }
}