package de.lambda9.ready2race.backend.app.raceTemplate.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.raceProperties.boundary.RacePropertiesService
import de.lambda9.ready2race.backend.app.raceProperties.control.RacePropertiesHasNamedParticipantRepo
import de.lambda9.ready2race.backend.app.raceProperties.control.RacePropertiesRepo
import de.lambda9.ready2race.backend.app.raceProperties.control.record
import de.lambda9.ready2race.backend.app.raceProperties.control.toUpdateFunction
import de.lambda9.ready2race.backend.app.raceTemplate.control.RaceTemplateRepo
import de.lambda9.ready2race.backend.app.raceTemplate.control.toDto
import de.lambda9.ready2race.backend.app.raceTemplate.entity.RaceTemplateDto
import de.lambda9.ready2race.backend.app.raceTemplate.entity.RaceTemplateRequest
import de.lambda9.ready2race.backend.app.raceTemplate.entity.RaceTemplateWithPropertiesSort
import de.lambda9.ready2race.backend.count
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceTemplateRecord
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.ktor.http.*
import java.time.LocalDateTime
import java.util.*

object RaceTemplateService {


    sealed interface RaceTemplateError : ServiceError {
        data object RaceTemplateNotFound : RaceTemplateError
        data object RacePropertiesNotFound : RaceTemplateError
        data object RaceCategoryUnknown : RaceTemplateError

        data class NamedParticipantsUnknown(val namedParticipants: List<UUID>) : RaceTemplateError

        override fun respond(): ApiError = when (this) {
            is RaceTemplateNotFound -> ApiError(
                status = HttpStatusCode.NotFound,
                message = "RaceTemplate not found"
            )

            is RacePropertiesNotFound -> ApiError(
                status = HttpStatusCode.NotFound,
                message = "No associated raceProperties found for the raceTemplate"
            )

            is RaceCategoryUnknown -> ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Referenced raceCategory unknown"
            )


            is NamedParticipantsUnknown -> ApiError(
                status = HttpStatusCode.BadRequest,
                message = "${"referenced namedParticipants".count(namedParticipants.size)} unknown",
                details = mapOf("unknownIds" to namedParticipants)
            )
        }
    }


    fun addRaceTemplate(
        request: RaceTemplateRequest,
        userId: UUID,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {
        val raceTemplateId = !RaceTemplateRepo.create(
            LocalDateTime.now().let { now ->
                RaceTemplateRecord(
                    id = UUID.randomUUID(),
                    createdAt = now,
                    createdBy = userId,
                    updatedAt = now,
                    updatedBy = userId
                )
            }
        ).orDie()

        !RacePropertiesService.checkNamedParticipantsExisting(request.properties.namedParticipants.map { it.namedParticipant })
        !RacePropertiesService.checkRaceCategoryExisting(request.properties.raceCategory)

        val racePropertiesId = !RacePropertiesRepo.create(request.properties.record(null, raceTemplateId)).orDie()

        !RacePropertiesHasNamedParticipantRepo.create(request.properties.namedParticipants.map {
            it.record(
                racePropertiesId
            )
        }).orDie()

        KIO.ok(ApiResponse.Created(raceTemplateId))
    }

    fun page(
        params: PaginationParameters<RaceTemplateWithPropertiesSort>
    ): App<Nothing, ApiResponse.Page<RaceTemplateDto, RaceTemplateWithPropertiesSort>> = KIO.comprehension {
        val total = !RaceTemplateRepo.countWithProperties(params.search).orDie()
        val page = !RaceTemplateRepo.pageWithProperties(params).orDie()

        page.forEachM { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getRaceTemplateWithProperties(
        templateId: UUID
    ): App<RaceTemplateError, ApiResponse> = KIO.comprehension {
        val race = !RaceTemplateRepo.getWithProperties(templateId).orDie()
            .onNullFail { RaceTemplateError.RaceTemplateNotFound }
        race.toDto().map { ApiResponse.Dto(it) }
    }

    fun updateRaceTemplate(
        templateId: UUID,
        request: RaceTemplateRequest,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !RacePropertiesService.checkNamedParticipantsExisting(request.properties.namedParticipants.map { it.namedParticipant })
        !RacePropertiesService.checkRaceCategoryExisting(request.properties.raceCategory)

        !RaceTemplateRepo.update(templateId) {
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().onNullFail { RaceTemplateError.RaceTemplateNotFound }

        // In theory the RacePropertiesRepo functions can't fail because there has to be a "properties" for the "race" to exist
        !RacePropertiesRepo
            .updateByRaceOrTemplate(templateId, request.properties.toUpdateFunction())
            .orDie()
            .onFalseFail { RaceTemplateError.RacePropertiesNotFound }

        val racePropertiesId =
            !RacePropertiesRepo.getIdByRaceOrTemplateId(templateId)
                .orDie()
                .onNullFail { RaceTemplateError.RacePropertiesNotFound }

        // delete and re-add the named participant entries
        !RacePropertiesHasNamedParticipantRepo.deleteManyByRaceProperties(racePropertiesId).orDie()
        !RacePropertiesHasNamedParticipantRepo.create(request.properties.namedParticipants.map {
            it.record(
                racePropertiesId
            )
        }).orDie()

        noData
    }

    fun deleteRaceTemplate(
        id: UUID
    ): App<RaceTemplateError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !RaceTemplateRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(RaceTemplateError.RaceTemplateNotFound)
        } else {
            noData
        }
    }
}