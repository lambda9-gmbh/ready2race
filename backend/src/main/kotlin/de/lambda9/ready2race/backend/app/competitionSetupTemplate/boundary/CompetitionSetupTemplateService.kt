package de.lambda9.ready2race.backend.app.competitionSetupTemplate.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService.getCompetitionSetupRoundsWithContent
import de.lambda9.ready2race.backend.app.competitionSetupTemplate.control.CompetitionSetupTemplateRepo
import de.lambda9.ready2race.backend.app.competitionSetupTemplate.control.toDto
import de.lambda9.ready2race.backend.app.competitionSetupTemplate.control.toOverviewDto
import de.lambda9.ready2race.backend.app.competitionSetupTemplate.entity.*
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupTemplateRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.UUID

object CompetitionSetupTemplateService {

    fun add(
        request: CompetitionSetupTemplateRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val competitionSetupTemplateId = !CompetitionSetupTemplateRepo.create(
            LocalDateTime.now().let { now ->
                CompetitionSetupTemplateRecord(
                    id = UUID.randomUUID(),
                    name = request.name,
                    description = request.description,
                    createdAt = now,
                    createdBy = userId,
                    updatedAt = now,
                    updatedBy = userId
                )
            }
        ).orDie()

        !CompetitionSetupService.updateCompetitionSetupRounds(request.rounds, null, competitionSetupTemplateId)

        KIO.ok(ApiResponse.Created(competitionSetupTemplateId))
    }

    fun page(
        params: PaginationParameters<CompetitionSetupTemplateSort>
    ): App<Nothing, ApiResponse.Page<CompetitionSetupTemplateDto, CompetitionSetupTemplateSort>> =
        KIO.comprehension {
            val total = !CompetitionSetupTemplateRepo.count(params.search).orDie()

            val page = !CompetitionSetupTemplateRepo.page(params).orDie()

            val dtos = !page.traverse {
                val rounds = !getCompetitionSetupRoundsWithContent(it.id)
                it.toDto(rounds)
            }

            KIO.ok(
                ApiResponse.Page(
                    data = dtos,
                    pagination = params.toPagination(total)
                )
            )
        }

    fun getById(
        competitionSetupTemplateId: UUID,
    ): App<ServiceError, ApiResponse.Dto<CompetitionSetupTemplateDto>> = KIO.comprehension {
        val record = !CompetitionSetupTemplateRepo.get(competitionSetupTemplateId).orDie()
            .onNullFail { CompetitionSetupTemplateError.NotFound }

        val roundDtos = !getCompetitionSetupRoundsWithContent(competitionSetupTemplateId)

        record.toDto(roundDtos).map { ApiResponse.Dto(it) }
    }

    fun update(
        request: CompetitionSetupTemplateRequest,
        userId: UUID,
        competitionSetupTemplateId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !CompetitionSetupTemplateRepo.update(competitionSetupTemplateId) {
            name = request.name
            description = request.description
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie().onNullFail { CompetitionSetupTemplateError.NotFound }

        !CompetitionSetupService.updateCompetitionSetupRounds(request.rounds, null, competitionSetupTemplateId)

        KIO.ok(ApiResponse.NoData)
    }

    fun delete(
        id: UUID
    ): App<CompetitionSetupTemplateError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !CompetitionSetupTemplateRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(CompetitionSetupTemplateError.NotFound)
        } else {
            noData
        }
    }

    fun getOverview(): App<Nothing, ApiResponse.ListDto<CompetitionSetupTemplateOverviewDto>> = KIO.comprehension {
        val records = !CompetitionSetupTemplateRepo.get().orDie()

        val dtos = !records.traverse { it.toOverviewDto() }

        KIO.ok(ApiResponse.ListDto(dtos))
    }
}