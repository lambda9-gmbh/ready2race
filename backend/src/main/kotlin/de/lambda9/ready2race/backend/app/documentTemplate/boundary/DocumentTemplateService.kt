package de.lambda9.ready2race.backend.app.documentTemplate.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.documentTemplate.control.*
import de.lambda9.ready2race.backend.app.documentTemplate.entity.*
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.requests.FileUpload
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateUsageRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentTemplateUsageRecord
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.util.*

object DocumentTemplateService {

    fun getTypes(
        eventId: UUID? = null,
    ): App<Nothing, ApiResponse.Dto<List<DocumentTypeDto>>> = KIO.comprehension {

        if (eventId == null) {
            DocumentTemplateUsageRepo.all().orDie().map { all ->
                all.associate { rec ->
                    rec.documentType to AssignedTemplateId(rec.template)
                }
            }
        } else {
            EventDocumentTemplateUsageRepo.getByEvent(eventId).orDie().map { byEvent ->
                byEvent.associate { rec ->
                    rec.documentType to AssignedTemplateId(rec.template)
                }
            }
        }.map { assignments ->
            ApiResponse.Dto(
                DocumentType.entries.map { type ->
                    DocumentTypeDto(
                        type = type,
                        assignedTemplate = assignments[type.name]
                    )
                }
            )
        }
    }

    fun addTemplate(
        upload: FileUpload,
        request: DocumentTemplateRequest,
    ): App<Nothing, ApiResponse.NoData> = KIO.comprehension {

        val record = !request.toRecord(upload.fileName)

        val id = !DocumentTemplateRepo.create(record).orDie()
        !DocumentTemplateDataRepo.create(
            DocumentTemplateDataRecord(
                template = id,
                data = upload.bytes,
            )
        ).orDie()

        noData
    }

    fun assignTemplate(
        docType: DocumentType,
        request: AssignDocumentTemplateRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        if (request.template != null) {
            !DocumentTemplateRepo.exists(request.template).orDie().onFalseFail { DocumentTemplateError.NotFound }

            if (request.event == null) {
                !DocumentTemplateUsageRepo.upsert(
                    DocumentTemplateUsageRecord(
                        documentType = docType.name,
                        template = request.template,
                    )
                ).orDie()
            } else {
                !EventRepo.exists(request.event).orDie().onFalseFail { EventError.NotFound }

                !EventDocumentTemplateUsageRepo.upsert(
                    EventDocumentTemplateUsageRecord(
                        documentType = docType.name,
                        template = request.template,
                        event = request.event,
                    )
                ).orDie()
            }
        } else if (request.event == null) {
            !DocumentTemplateUsageRepo.delete(docType).orDie()
        } else {
            !EventRepo.exists(request.event).orDie().onFalseFail { EventError.NotFound }

            !EventDocumentTemplateUsageRepo.upsert(
                EventDocumentTemplateUsageRecord(
                    documentType = docType.name,
                    template = null,
                    event = request.event,
                )
            ).orDie()
        }

        noData
    }

    fun page(
        params: PaginationParameters<DocumentTemplateSort>
    ): App<Nothing, ApiResponse.Page<DocumentTemplateDto, DocumentTemplateSort>> = KIO.comprehension {
        val total = !DocumentTemplateRepo.count(params.search).orDie()
        val page = !DocumentTemplateRepo.page(params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }
}