package de.lambda9.ready2race.backend.app.eventDocumentType.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.eventDocumentType.control.EventDocumentTypeRepo
import de.lambda9.ready2race.backend.app.eventDocumentType.control.toDto
import de.lambda9.ready2race.backend.app.eventDocumentType.control.toRecord
import de.lambda9.ready2race.backend.app.eventDocumentType.entity.EventDocumentTypeDto
import de.lambda9.ready2race.backend.app.eventDocumentType.entity.EventDocumentTypeError
import de.lambda9.ready2race.backend.app.eventDocumentType.entity.EventDocumentTypeRequest
import de.lambda9.ready2race.backend.app.eventDocumentType.entity.EventDocumentTypeSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object EventDocumentTypeService {

    fun addDocumentType(
        request: EventDocumentTypeRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {

        val record = !request.toRecord(userId)
        EventDocumentTypeRepo.create(record).orDie().map { ApiResponse.Created(it) }

    }

    fun page(
        params: PaginationParameters<EventDocumentTypeSort>,
    ): App<Nothing, ApiResponse.Page<EventDocumentTypeDto, EventDocumentTypeSort>> = KIO.comprehension {
        val total = !EventDocumentTypeRepo.count(params.search).orDie()
        val page = !EventDocumentTypeRepo.page(params).orDie()

        page.forEachM { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun updateDocumentType(
        id: UUID,
        request: EventDocumentTypeRequest,
        userId: UUID
    ): App<EventDocumentTypeError, ApiResponse.NoData> =
        EventDocumentTypeRepo.update(id) {
            name = request.name
            required = request.required
            confirmationRequired = request.confirmationRequired
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()
            .onNullFail { EventDocumentTypeError.NotFound }
            .map { ApiResponse.NoData }

    fun deleteDocumentType(
        id: UUID,
    ): App<EventDocumentTypeError, ApiResponse.NoData> =
        EventDocumentTypeRepo.delete(id).orDie().failIf({ it < 1 }) { EventDocumentTypeError.NotFound }
            .map { ApiResponse.NoData }
}