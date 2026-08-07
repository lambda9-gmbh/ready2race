package de.lambda9.ready2race.backend.app.documentTemplate.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.certificate.boundary.CertificateService
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentPlaceholderRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentTemplateDataRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentTemplateFontRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentTemplateRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentTemplateUsageRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.toDto
import de.lambda9.ready2race.backend.app.documentTemplate.control.toGapPlaceholders
import de.lambda9.ready2race.backend.app.documentTemplate.control.toRecord
import de.lambda9.ready2race.backend.app.documentTemplate.control.toRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.AssignGapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.AssignedTemplateId
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateDto
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateError
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateViewSort
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTypeDto
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapPlaceholderValues
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.responses.noDataResponse
import de.lambda9.ready2race.backend.calls.responses.pageResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.GapDocumentTemplateDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.GapDocumentTemplateFontRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.GapDocumentTemplateUsageRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.pdf.checkValidFont
import de.lambda9.ready2race.backend.pdf.checkValidPdf
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.UUID

object GapDocumentTemplateService {

    fun getTypes(): App<Nothing, ApiResponse.Dto<List<GapDocumentTypeDto>>> =
        GapDocumentTemplateUsageRepo.all().orDie().map { all ->
            val usages = all.associate { rec ->
                rec.type to AssignedTemplateId(rec.template)
            }

            ApiResponse.Dto(
                GapDocumentType.entries.map { type ->
                    GapDocumentTypeDto(
                        type = type,
                        assignedTemplate = usages[type.name],
                        allowedPlaceholders = type.allowedPlaceholders.toList(),
                    )
                }
            )
        }

    fun page(
        params: PaginationParameters<GapDocumentTemplateViewSort>
    ): App<Nothing, ApiResponse.Page<GapDocumentTemplateDto, GapDocumentTemplateViewSort>> =
        GapDocumentTemplateRepo.page(params).orDie().pageResponse { it.toDto() }

    fun addTemplate(
        file: File,
        request: GapDocumentTemplateRequest,
        font: File?,
    ): App<GapDocumentTemplateError, ApiResponse.NoData> = KIO.comprehension {

        !KIO.failOn(!checkValidPdf(file.bytes)) { GapDocumentTemplateError.InvalidPdf }

        !KIO.failOn(!GapDocumentTemplateLogic.placeholdersFitOnSinglePage(request.type, request.placeholders)) {
            GapDocumentTemplateError.PlaceholderPageNotSupported
        }

        !KIO.failOn(!GapDocumentTemplateLogic.placeholderTypesAreAllowed(request.type, request.placeholders)) {
            GapDocumentTemplateError.PlaceholderTypeNotSupported
        }

        if (font != null && font.bytes.isNotEmpty()) {
            !KIO.failOn(!GapDocumentTemplateLogic.hasValidFontExtension(font.name)) {
                GapDocumentTemplateError.InvalidFont
            }
            !KIO.failOn(!checkValidFont(font.bytes)) { GapDocumentTemplateError.InvalidFont }
        }

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

        if (font != null && font.bytes.isNotEmpty()) {
            !GapDocumentTemplateFontRepo.upsert(
                GapDocumentTemplateFontRecord(
                    template = id,
                    fileName = font.name,
                    data = font.bytes,
                )
            ).orDie()
        }

        noData

    }

    fun updateTemplate(
        id: UUID,
        request: GapDocumentTemplateRequest,
        font: File?,
    ): App<GapDocumentTemplateError, ApiResponse.NoData> = KIO.comprehension {

        !KIO.failOn(!GapDocumentTemplateLogic.placeholdersFitOnSinglePage(request.type, request.placeholders)) {
            GapDocumentTemplateError.PlaceholderPageNotSupported
        }

        !KIO.failOn(!GapDocumentTemplateLogic.placeholderTypesAreAllowed(request.type, request.placeholders)) {
            GapDocumentTemplateError.PlaceholderTypeNotSupported
        }

        if (font != null && font.bytes.isNotEmpty()) {
            !KIO.failOn(!GapDocumentTemplateLogic.hasValidFontExtension(font.name)) {
                GapDocumentTemplateError.InvalidFont
            }
            !KIO.failOn(!checkValidFont(font.bytes)) { GapDocumentTemplateError.InvalidFont }
        }

        !GapDocumentTemplateRepo.update(id) {
            type = request.type.name
            fontName = request.fontName
        }.orDie()
            .onNullFail { GapDocumentTemplateError.NotFound }

        !GapDocumentPlaceholderRepo.deleteByTemplate(id).orDie()

        val records = request.placeholders.map { it.toRecord(id) }

        !GapDocumentPlaceholderRepo.create(records).orDie()

        // Ein Font-Part ohne Inhalt signalisiert das Entfernen der bisherigen Schrift; kein Part
        // (font == null) lässt eine vorhandene Schrift unangetastet.
        if (font != null) {
            if (font.bytes.isEmpty()) {
                !GapDocumentTemplateFontRepo.delete(id).orDie()
            } else {
                !GapDocumentTemplateFontRepo.upsert(
                    GapDocumentTemplateFontRecord(
                        template = id,
                        fileName = font.name,
                        data = font.bytes,
                    )
                ).orDie()
            }
        }

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

    /**
     * Die Vorlage als Austauschpaket: PDF, Platzhalter und Schrift in einer Datei, damit dieselbe
     * Einrichtung in einer anderen Instanz nicht von Hand nachgebaut werden muss.
     */
    fun exportTemplate(
        id: UUID,
    ): App<GapDocumentTemplateError, ApiResponse.File> = KIO.comprehension {
        val pdf = !GapDocumentTemplateDataRepo.getData(id).orDie().onNullFail { GapDocumentTemplateError.NotFound }
        val template = !GapDocumentTemplateRepo.get(id).orDie().onNullDie("foreign key constraint")
        val fontRecord = !GapDocumentTemplateFontRepo.get(id).orDie()

        val placeholders = template.placeholders!!.toList().map { it!!.toDto().toRequest() }

        val bytes = GapDocumentTemplatePackage.write(
            GapDocumentTemplatePackage.Content(
                name = template.name!!,
                request = GapDocumentTemplateRequest(
                    type = GapDocumentType.valueOf(template.type!!),
                    placeholders = placeholders,
                    fontName = template.fontName,
                ),
                pdf = pdf,
                font = fontRecord?.let { File(it.fileName, it.data) },
            )
        )

        KIO.ok(
            ApiResponse.File(
                name = "${template.name!!.substringBeforeLast('.')}.r2rtpl.zip",
                bytes = bytes,
            )
        )
    }

    fun getPreview(
        id: UUID
    ): App<GapDocumentTemplateError, ApiResponse.File> = KIO.comprehension {

        val templateBytes =
            !GapDocumentTemplateDataRepo.getData(id).orDie().onNullFail { GapDocumentTemplateError.NotFound }
        val template = !GapDocumentTemplateRepo.get(id).orDie().onNullDie("foreign key constraint")

        val bytes = CertificateService.participantForEvent(
            additions = GapPlaceholderLogic.fill(
                placeholders = template.placeholders!!.toList().toGapPlaceholders(),
                values = previewValues,
            ),
            template = templateBytes,
        )

        KIO.ok(
            ApiResponse.File(
                name = "sample.pdf",
                bytes = bytes,
            )
        )
    }

    private val previewValues = GapPlaceholderValues(
        firstName = "Max",
        lastName = "Mustermann",
        fullName = "Max Mustermann",
        result = "3492 m",
        eventName = "Summer Sport Festival",
        place = "1. Platz",
        competitionName = "CF 1x Frauen-Einer",
        competitionShortName = "CF 1x",
        clubName = "Ruderklub Flensburg",
        teamName = "Flensburg I",
        eventDate = "16.–17. August 2026",
        eventLocation = "Flensburg",
    )

    fun assignTemplate(
        type: GapDocumentType,
        request: AssignGapDocumentTemplateRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        if (request.template == null) {
            !GapDocumentTemplateUsageRepo.delete(type).orDie()
        } else {
            val template = !GapDocumentTemplateRepo.get(request.template).orDie()
                .onNullFail { GapDocumentTemplateError.NotFound }

            // Ohne diese Prüfung ließe sich z. B. eine Teilnahmeurkunden-Vorlage unter
            // AWARD_CERTIFICATE einhängen; die Generierung würde dann nur die zufällig
            // überlappenden Platzhalter befüllen, ohne jede Fehlermeldung (siehe GapDocumentTemplateLogic).
            !KIO.failOn(!GapDocumentTemplateLogic.templateTypeMatches(GapDocumentType.valueOf(template.type!!), type)) {
                GapDocumentTemplateError.TemplateTypeMismatch
            }

            !GapDocumentTemplateUsageRepo.upsert(
                GapDocumentTemplateUsageRecord(
                    type = type.name,
                    template = request.template,
                )
            ).orDie()
        }

        noData
    }
}