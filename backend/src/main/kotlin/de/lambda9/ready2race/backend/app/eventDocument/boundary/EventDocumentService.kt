package de.lambda9.ready2race.backend.app.eventDocument.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDocument.control.EventDocumentDataRepo
import de.lambda9.ready2race.backend.app.eventDocument.control.EventDocumentRepo
import de.lambda9.ready2race.backend.app.eventDocument.control.toDto
import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentDto
import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentError
import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentRequest
import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentViewSort
import de.lambda9.ready2race.backend.app.eventDocumentType.control.EventDocumentTypeRepo
import de.lambda9.ready2race.backend.app.eventDocumentType.entity.EventDocumentTypeError
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationError
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentRecord
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.requests.FileUpload
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object EventDocumentService {

    fun page(
        params: PaginationParameters<EventDocumentViewSort>,
    ): App<Nothing, ApiResponse.Page<EventDocumentDto, EventDocumentViewSort>> = KIO.comprehension {
        val total = !EventDocumentRepo.count(params.search).orDie()
        val page = !EventDocumentRepo.page(params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun saveDocuments(
        eventId: UUID,
        uploads: List<FileUpload>,
        documentTypeId: UUID?,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventRepo.exists(eventId).orDie().onFalseFail { EventError.NotFound }
        if (documentTypeId != null) {
            !EventDocumentTypeRepo.exists(documentTypeId).orDie().onFalseFail { EventDocumentTypeError.NotFound }
        }
        val now = LocalDateTime.now()
        uploads.forEach { (name, bytes) ->
            val id = !EventDocumentRepo.create(
                EventDocumentRecord(
                    id = UUID.randomUUID(),
                    name = name,
                    eventDocumentType = documentTypeId,
                    event = eventId,
                    createdAt = now,
                    createdBy = userId,
                    updatedAt = now,
                    updatedBy = userId,
                )
            ).orDie()

            !EventDocumentDataRepo.create(
                EventDocumentDataRecord(
                    eventDocument = id,
                    data = bytes,
                )
            ).orDie()
        }

        noData
    }

    fun downloadDocument(
        id: UUID,
        scope: Privilege.Scope
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {

        val document = !EventDocumentRepo.getDownload(id).orDie().onNullFail { EventDocumentError.NotFound }

        if (scope == Privilege.Scope.OWN) {
            !EventRepo.isOpenForRegistration(document.event!!, LocalDateTime.now()).orDie()
                .onFalseFail { EventRegistrationError.RegistrationClosed }
        }

        KIO.ok(
            ApiResponse.File(
                name = document.name!!,
                bytes = document.data!!,
            )
        )

    }

    fun updateDocument(
        id: UUID,
        request: EventDocumentRequest,
        userId: UUID
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        // todo: @incomplete check, if document belongs to correct event by pathParam, also in other endpoints for put / delete ...
        if (request.documentType != null) {
            !EventDocumentTypeRepo.exists(request.documentType).orDie().onFalseFail { EventDocumentTypeError.NotFound }
        }

        EventDocumentRepo.update(id) {
            eventDocumentType = request.documentType
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie().map { ApiResponse.NoData }
    }

    fun deleteDocument(
        id: UUID,
    ): App<EventDocumentError, ApiResponse.NoData> =
        EventDocumentRepo.delete(id).orDie().failIf({ it < 1 }) { EventDocumentError.NotFound }
            .map { ApiResponse.NoData }
}