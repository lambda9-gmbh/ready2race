package de.lambda9.ready2race.backend.app.competitionTemplate.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionProperties.boundary.CompetitionPropertiesService
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionProperties.control.toRecord
import de.lambda9.ready2race.backend.app.competitionProperties.control.toUpdateFunction
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesRequest
import de.lambda9.ready2race.backend.app.competitionTemplate.control.CompetitionTemplateRepo
import de.lambda9.ready2race.backend.app.competitionTemplate.control.toDto
import de.lambda9.ready2race.backend.app.competitionTemplate.entity.CompetitionTemplateDto
import de.lambda9.ready2race.backend.app.competitionTemplate.entity.CompetitionTemplateError
import de.lambda9.ready2race.backend.app.competitionTemplate.entity.CompetitionTemplateWithPropertiesSort
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionTemplateRecord
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object CompetitionTemplateService {

    fun addCompetitionTemplate(
        request: CompetitionPropertiesRequest,
        userId: UUID,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {
        val competitionTemplateId = !CompetitionTemplateRepo.create(
            LocalDateTime.now().let { now ->
                CompetitionTemplateRecord(
                    id = UUID.randomUUID(),
                    competitionSetupTemplate = request.setupTemplate,
                    createdAt = now,
                    createdBy = userId,
                    updatedAt = now,
                    updatedBy = userId
                )
            }
        ).orDie()

        !CompetitionPropertiesService.checkRequestReferences(request)

        val competitionPropertiesId =
            !CompetitionPropertiesRepo.create(request.toRecord(null, competitionTemplateId)).orDie()

        !CompetitionPropertiesService.addCompetitionPropertiesReferences(
            namedParticipants = request.namedParticipants.map { it.toRecord(competitionPropertiesId) },
            fees = request.fees.map { it.toRecord(competitionPropertiesId) }
        )

        KIO.ok(ApiResponse.Created(competitionTemplateId))
    }

    fun page(
        params: PaginationParameters<CompetitionTemplateWithPropertiesSort>
    ): App<Nothing, ApiResponse.Page<CompetitionTemplateDto, CompetitionTemplateWithPropertiesSort>> =
        KIO.comprehension {
            val total = !CompetitionTemplateRepo.countWithProperties(params.search).orDie()
            val page = !CompetitionTemplateRepo.pageWithProperties(params).orDie()

            page.traverse { it.toDto() }.map {
                ApiResponse.Page(
                    data = it,
                    pagination = params.toPagination(total)
                )
            }
        }

    fun getCompetitionTemplateWithProperties(
        templateId: UUID
    ): App<CompetitionTemplateError, ApiResponse> = KIO.comprehension {
        val competition = !CompetitionTemplateRepo.getWithProperties(templateId).orDie()
            .onNullFail { CompetitionTemplateError.NotFound }
        competition.toDto().map { ApiResponse.Dto(it) }
    }

    fun updateCompetitionTemplate(
        templateId: UUID,
        request: CompetitionPropertiesRequest,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !CompetitionPropertiesService.checkRequestReferences(request)

        !CompetitionTemplateRepo.update(templateId) {
            competitionSetupTemplate = request.setupTemplate
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().onNullFail { CompetitionTemplateError.NotFound }

        // In theory the CompetitionPropertiesRepo functions can't fail because there has to be a "properties" for the "competition" to exist
        !CompetitionPropertiesRepo
            .updateByCompetitionOrTemplate(templateId, request.toUpdateFunction())
            .orDie()
            .onNullFail { CompetitionTemplateError.CompetitionPropertiesNotFound }

        val competitionPropertiesId =
            !CompetitionPropertiesRepo.getIdByCompetitionOrTemplateId(templateId)
                .orDie()
                .onNullFail { CompetitionTemplateError.CompetitionPropertiesNotFound }

        !CompetitionPropertiesService.updateCompetitionPropertiesReferences(
            competitionPropertiesId = competitionPropertiesId,
            namedParticipants = request.namedParticipants.map { it.toRecord(competitionPropertiesId) },
            fees = request.fees.map { it.toRecord(competitionPropertiesId) })

        noData
    }

    fun deleteCompetitionTemplate(
        id: UUID
    ): App<CompetitionTemplateError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !CompetitionTemplateRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(CompetitionTemplateError.NotFound)
        } else {
            noData
        }
    }

}