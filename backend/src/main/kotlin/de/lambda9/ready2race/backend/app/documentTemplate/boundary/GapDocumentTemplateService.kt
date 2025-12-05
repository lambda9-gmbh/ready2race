package de.lambda9.ready2race.backend.app.documentTemplate.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.certificate.boundary.CertificateService
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentPlaceholderRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentTemplateDataRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentTemplateRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.toDto
import de.lambda9.ready2race.backend.app.documentTemplate.control.toRecord
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateDto
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateError
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateViewSort
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.responses.noDataResponse
import de.lambda9.ready2race.backend.calls.responses.pageResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.GapDocumentTemplateDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.pdf.AdditionalText
import de.lambda9.ready2race.backend.text.TextAlign
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.lang.Exception
import java.util.UUID

object GapDocumentTemplateService {

    fun page(
        params: PaginationParameters<GapDocumentTemplateViewSort>
    ): App<Nothing, ApiResponse.Page<GapDocumentTemplateDto, GapDocumentTemplateViewSort>> =
        GapDocumentTemplateRepo.page(params).orDie().pageResponse { it.toDto() }

    fun addTemplate(
        file: File,
        request: GapDocumentTemplateRequest,
    ): App<Nothing, ApiResponse.NoData> = KIO.comprehension {

        val templateRecord = request.toRecord(file.name)

        val id = !GapDocumentTemplateRepo.create(templateRecord).orDie()

        val placeholderRecords = request.placeholders.map { it.toRecord(id) }

        !GapDocumentPlaceholderRepo.create(placeholderRecords).orDie()

        !GapDocumentTemplateDataRepo.create(
            GapDocumentTemplateDataRecord(
                template = id,
                data = file.bytes,
            )
        ).orDie()

        noData

    }

    fun updateTemplate(
        id: UUID,
        request: GapDocumentTemplateRequest,
    ): App<GapDocumentTemplateError, ApiResponse.NoData> = KIO.comprehension {

        !GapDocumentTemplateRepo.update(id) {
            type = request.type.name
        }.orDie()
            .onNullFail { GapDocumentTemplateError.NotFound }

        !GapDocumentPlaceholderRepo.deleteByTemplate(id).orDie()

        val records = request.placeholders.map { it.toRecord(id) }

        !GapDocumentPlaceholderRepo.create(records).orDie()

        noData

    }

    fun deleteTemplate(
        id: UUID,
    ): App<GapDocumentTemplateError, ApiResponse.NoData> =
        GapDocumentTemplateRepo.delete(id).orDie().failIf({ it < 1}) { GapDocumentTemplateError.NotFound }
            .noDataResponse()

    fun download(
        id: UUID,
    ): App<GapDocumentTemplateError, ApiResponse.File> = KIO.comprehension {
        val bytes = !GapDocumentTemplateDataRepo.getData(id).orDie().onNullFail { GapDocumentTemplateError.NotFound }
        val template = !GapDocumentTemplateRepo.get(id).orDie().onNullDie("foreign key constraint")

        KIO.ok(
            ApiResponse.File(
                name = template.name!!,
                bytes = bytes,
            )
        )
    }

    fun getPreview(
        id: UUID
    ): App<GapDocumentTemplateError, ApiResponse.File> = KIO.comprehension {

        val templateBytes = !GapDocumentTemplateDataRepo.getData(id).orDie().onNullFail { GapDocumentTemplateError.NotFound }
        val template = !GapDocumentTemplateRepo.get(id).orDie().onNullDie("foreign key constraint")

        val type = GapDocumentType.valueOf(template.type!!)

        when (type) {
            GapDocumentType.CERTIFICATE_OF_PARTICIPATION -> CertificateService.participantForEvent(
                additions = template.placeholders!!.mapNotNull {
                    println("1 placeholder found")
                    val type =
                        try {
                            GapDocumentPlaceholderType.valueOf(it!!.type)
                        } catch (ex: Exception) {
                            return@mapNotNull null
                        }

                    AdditionalText(
                        content = when (type) {
                            GapDocumentPlaceholderType.FIRST_NAME -> "Max"
                            GapDocumentPlaceholderType.LAST_NAME -> "Mustermann"
                            GapDocumentPlaceholderType.FULL_NAME -> "Max Mustermann"
                            GapDocumentPlaceholderType.PLACE -> "2"
                            GapDocumentPlaceholderType.RESULT -> "3492 m"
                            GapDocumentPlaceholderType.EVENT_NAME -> "Summer Sport Festival"
                        },
                        page = it.page,
                        relLeft = it.relLeft,
                        relTop = it.relTop,
                        relWidth = it.relWidth,
                        relHeight = it.relHeight,
                        textAlign = TextAlign.valueOf(it.textAlign)
                    )
                },
                template = templateBytes,
            )
        }.let {
            KIO.ok(
                ApiResponse.File(
                    name = "sample.pdf",
                    bytes = it,
                )
            )
        }
    }
}