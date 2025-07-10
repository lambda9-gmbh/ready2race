package de.lambda9.ready2race.backend.app.documentTemplate.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchData
import de.lambda9.ready2race.backend.app.documentTemplate.control.*
import de.lambda9.ready2race.backend.app.documentTemplate.entity.*
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventRegistration.boundary.EventRegistrationService
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationResultData
import de.lambda9.ready2race.backend.app.invoice.boundary.InvoiceService
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceData
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.requests.FileUpload
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateUsageRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentTemplateUsageRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationResultViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoicePositionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceRecord
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.PageTemplate
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
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

    fun getPreview(
        id: UUID,
        type: DocumentType,
    ): App<DocumentTemplateError, ApiResponse.File> = KIO.comprehension {

        val templateBytes = !DocumentTemplateDataRepo.getData(id).orDie().onNullFail { DocumentTemplateError.NotFound }
        val templateRecord = !DocumentTemplateRepo.get(id).orDie().onNullDie("foreign key constraint")

        val template = PageTemplate(
            bytes = templateBytes,
            pagepadding = Padding.fromMillimetersOrDefault(
                top = templateRecord.pagePaddingTop?.toFloat(),
                left = templateRecord.pagePaddingLeft?.toFloat(),
                bottom = templateRecord.pagePaddingBottom?.toFloat(),
                right = templateRecord.pagePaddingRight?.toFloat(),
            )
        )

        when (type) {
            DocumentType.REGISTRATION_REPORT -> EventRegistrationService.buildPdf(
                EventRegistrationResultData(
                    competitionRegistrations = listOf(
                        EventRegistrationResultData.CompetitionRegistrationData(
                            identifier = "S",
                            name = "Einzelrennen",
                            shortName = "Solo",
                            clubRegistrations = listOf(
                                EventRegistrationResultData.ClubRegistrationData(
                                    name = "Ruderclub",
                                    teams = listOf(
                                        EventRegistrationResultData.TeamRegistrationData(
                                            name = "#1",
                                            participants = listOf(
                                                EventRegistrationResultData.ParticipantRegistrationData(
                                                    role = "Teilnehmer",
                                                    firstname = "Max",
                                                    lastname = "Mustermann",
                                                    year = 1990,
                                                    gender = Gender.M,
                                                    externalClubName = null
                                                )
                                            )
                                        ),
                                        EventRegistrationResultData.TeamRegistrationData(
                                            name = "#2",
                                            participants = listOf(
                                                EventRegistrationResultData.ParticipantRegistrationData(
                                                    role = "Teilnehmer",
                                                    firstname = "Marcus",
                                                    lastname = "Mustermann",
                                                    year = 2000,
                                                    gender = Gender.M,
                                                    externalClubName = null
                                                )
                                            )
                                        ),
                                        EventRegistrationResultData.TeamRegistrationData(
                                            name = "#3",
                                            participants = listOf(
                                                EventRegistrationResultData.ParticipantRegistrationData(
                                                    role = "Teilnehmer",
                                                    firstname = "Manfred",
                                                    lastname = "Mustermann",
                                                    year = 1970,
                                                    gender = Gender.M,
                                                    externalClubName = "1. RC"
                                                )
                                            )
                                        )
                                    )
                                ),
                                EventRegistrationResultData.ClubRegistrationData(
                                    name = "Rudern",
                                    teams = listOf(
                                        EventRegistrationResultData.TeamRegistrationData(
                                            name = null,
                                            participants = listOf(
                                                EventRegistrationResultData.ParticipantRegistrationData(
                                                    role = "Teilnehmer",
                                                    firstname = "Sebastian",
                                                    lastname = "Jensen",
                                                    year = 1994,
                                                    gender = Gender.M,
                                                    externalClubName = null
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                        EventRegistrationResultData.CompetitionRegistrationData(
                            identifier = "D",
                            name = "Teamrennen",
                            shortName = "Duo",
                            clubRegistrations = listOf(
                                EventRegistrationResultData.ClubRegistrationData(
                                    name = "Ruderclub",
                                    teams = listOf(
                                        EventRegistrationResultData.TeamRegistrationData(
                                            name = "#1",
                                            participants = listOf(
                                                EventRegistrationResultData.ParticipantRegistrationData(
                                                    role = "Teilnehmer",
                                                    firstname = "Max",
                                                    lastname = "Mustermann",
                                                    year = 1990,
                                                    gender = Gender.M,
                                                    externalClubName = null
                                                ),
                                                EventRegistrationResultData.ParticipantRegistrationData(
                                                    role = "Teilnehmer",
                                                    firstname = "Marcus",
                                                    lastname = "Mustermann",
                                                    year = 2000,
                                                    gender = Gender.M,
                                                    externalClubName = null
                                                )
                                            )
                                        ),
                                        EventRegistrationResultData.TeamRegistrationData(
                                            name = "#2",
                                            participants = listOf(
                                                EventRegistrationResultData.ParticipantRegistrationData(
                                                    role = "Teilnehmer",
                                                    firstname = "Manfred",
                                                    lastname = "Mustermann",
                                                    year = 1970,
                                                    gender = Gender.M,
                                                    externalClubName = "1. RC"
                                                ),
                                                EventRegistrationResultData.ParticipantRegistrationData(
                                                    role = "Teilnehmer",
                                                    firstname = "Bettina",
                                                    lastname = "Mustermann",
                                                    year = 1974,
                                                    gender = Gender.F,
                                                    externalClubName = null
                                                )
                                            )
                                        )
                                    )
                                ),
                                EventRegistrationResultData.ClubRegistrationData(
                                    name = "Rudern",
                                    teams = listOf(
                                        EventRegistrationResultData.TeamRegistrationData(
                                            name = null,
                                            participants = listOf(
                                                EventRegistrationResultData.ParticipantRegistrationData(
                                                    role = "Teilnehmer",
                                                    firstname = "Sebastian",
                                                    lastname = "Jensen",
                                                    year = 1994,
                                                    gender = Gender.M,
                                                    externalClubName = null
                                                ),
                                                EventRegistrationResultData.ParticipantRegistrationData(
                                                    role = "Teilnehmer",
                                                    firstname = "Harald",
                                                    lastname = "Jensen",
                                                    year = 1979,
                                                    gender = Gender.M,
                                                    externalClubName = null
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                        EventRegistrationResultData.CompetitionRegistrationData(
                            identifier = "N",
                            name = "KeinRennen",
                            shortName = "No",
                            clubRegistrations = emptyList(),
                        )
                    )
                ),
                template,
            )

            DocumentType.INVOICE -> InvoiceService.buildPdf(
                InvoiceData(
                    eventName = "Beispielveranstaltung",
                    invoiceNumber = "BSP-11",
                    contact = InvoiceData.InvoiceContactData(
                        name = "Ruderklub",
                        street = "Musterstraße 42",
                        zip = "12345",
                        city = "Musterhausen",
                        email = "info@rk-example.com",
                    ),
                    payee = InvoiceData.InvoicePayeeData(
                        holder = "Ruderklub Schatzmeister",
                        iban = "DE12123412341234123412",
                        bic = "ABCDEFGH",
                        bank = "Musterbank",
                    ),
                    billedToOrga = "1. RC",
                    billedToName = "Karsten Holz",
                    paymentDueBy = LocalDate.now().plusWeeks(2),
                    createdAt = LocalDateTime.now(),
                    positions = listOf(
                        InvoiceData.InvoicePositionData(
                            position = 1,
                            item = "Meldegebühr",
                            description = "S - Einzelrennen",
                            quantity = BigDecimal("3"),
                            unitPrice = BigDecimal("50"),
                        ),
                        InvoiceData.InvoicePositionData(
                            position = 2,
                            item = "Bootsausleih",
                            description = "S - Einzelrennen",
                            quantity = BigDecimal("1"),
                            unitPrice = BigDecimal("30"),
                        ),
                    )
                ),
                template,
            )

            DocumentType.START_LIST -> CompetitionExecutionService.buildPdf(
                CompetitionMatchData(
                    startTime = LocalDateTime.now().plusHours(1),
                    startTimeOffset = null,
                    competition = CompetitionMatchData.CompetitionData(
                        identifier = "2",
                        name = "Beispielwettkampf",
                        shortName = "BspW 1",
                        category = null,
                    ),
                    teams = listOf(
                        CompetitionMatchData.CompetitionMatchTeam(
                            startNumber = 1,
                            clubName = "Sportclub Musterhausen",
                            teamName = "#1",
                            participants = listOf(
                                CompetitionMatchData.CompetitionMatchParticipant(
                                    role = "Teilnehmer",
                                    firstname = "Max",
                                    lastname = "Mustermann",
                                    year = 1970,
                                    gender = Gender.M,
                                    externalClubName = null
                                )
                            )
                        ),
                        CompetitionMatchData.CompetitionMatchTeam(
                            startNumber = 2,
                            clubName = "Sportclub Musterhausen",
                            teamName = "#2",
                            participants = listOf(
                                CompetitionMatchData.CompetitionMatchParticipant(
                                    role = "Teilnehmer",
                                    firstname = "Marcus",
                                    lastname = "König",
                                    year = 1980,
                                    gender = Gender.M,
                                    externalClubName = null
                                )
                            )
                        ),
                        CompetitionMatchData.CompetitionMatchTeam(
                            startNumber = 3,
                            clubName = "Neustadt 101",
                            teamName = null,
                            participants = listOf(
                                CompetitionMatchData.CompetitionMatchParticipant(
                                    role = "Teilnehmer",
                                    firstname = "Hänno",
                                    lastname = "Klausen",
                                    year = 1992,
                                    gender = Gender.M,
                                    externalClubName = null
                                )
                            )
                        ),
                        CompetitionMatchData.CompetitionMatchTeam(
                            startNumber = 4,
                            clubName = "Sportfreunde e.V.",
                            teamName = null,
                            participants = listOf(
                                CompetitionMatchData.CompetitionMatchParticipant(
                                    role = "Teilnehmer",
                                    firstname = "John",
                                    lastname = "Doe",
                                    year = 1990,
                                    gender = Gender.M,
                                    externalClubName = null
                                )
                            )
                        ),
                    )
                ),
                template,
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

    fun updateTemplate(
        id: UUID,
        request: DocumentTemplateRequest,
    ): App<DocumentTemplateError, ApiResponse.NoData> =
        DocumentTemplateRepo.update(id) {
            pagePaddingTop = request.pagePaddingTop
            pagePaddingRight = request.pagePaddingRight
            pagePaddingBottom = request.pagePaddingBottom
            pagePaddingLeft = request.pagePaddingLeft
        }.orDie()
            .onNullFail { DocumentTemplateError.NotFound }
            .map {ApiResponse.NoData }

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

    fun deleteTemplate(
        id: UUID,
    ): App<DocumentTemplateError, ApiResponse.NoData> =
        DocumentTemplateRepo.delete(id).orDie().failIf({ it < 1}) { DocumentTemplateError.NotFound }
            .map { ApiResponse.NoData }
}