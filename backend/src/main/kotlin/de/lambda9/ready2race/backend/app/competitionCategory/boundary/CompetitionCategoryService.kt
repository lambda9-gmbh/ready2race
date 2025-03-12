package de.lambda9.ready2race.backend.app.competitionCategory.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionCategory.control.CompetitionCategoryRepo
import de.lambda9.ready2race.backend.app.competitionCategory.control.competitionCategoryDto
import de.lambda9.ready2race.backend.app.competitionCategory.control.toRecord
import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategoryDto
import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategoryError
import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategoryRequest
import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategorySort
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionProperties.entity.splitTemplatesAndCompetitions
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object CompetitionCategoryService {

    fun addCompetitionCategory(
        request: CompetitionCategoryRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        val competitionCategoryId = !CompetitionCategoryRepo.create(record).orDie()
        KIO.ok(ApiResponse.Created(competitionCategoryId))
    }

    fun page(
        params: PaginationParameters<CompetitionCategorySort>
    ): App<Nothing, ApiResponse.Page<CompetitionCategoryDto, CompetitionCategorySort>> = KIO.comprehension {
        val total = !CompetitionCategoryRepo.count(params.search).orDie()
        val page = !CompetitionCategoryRepo.page(params).orDie()

        page.traverse { it.competitionCategoryDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }


    fun updateCompetitionCategory(
        competitionId: UUID,
        request: CompetitionCategoryRequest,
        userId: UUID,
    ): App<CompetitionCategoryError, ApiResponse.NoData> = KIO.comprehension {
        !CompetitionCategoryRepo.update(competitionId) {
            name = request.name
            description = request.description
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie().onNullFail { CompetitionCategoryError.NotFound }

        noData
    }

    fun deleteCompetitionCategory(
        competitionCategoryId: UUID
    ): App<CompetitionCategoryError, ApiResponse.NoData> = KIO.comprehension {
        val propertiesContainingCategory =
            !CompetitionPropertiesRepo.getByCompetitionCategory(competitionCategoryId).orDie()
                .map { it.splitTemplatesAndCompetitions() }

        if (propertiesContainingCategory.containsEntries()) {
            return@comprehension KIO.fail(
                CompetitionCategoryError.CompetitionCategoryInUse(propertiesContainingCategory)
            )
        }

        val deleted = !CompetitionCategoryRepo.delete(competitionCategoryId).orDie()

        if (deleted < 1) {
            KIO.fail(CompetitionCategoryError.NotFound)
        } else {
            noData
        }
    }

}