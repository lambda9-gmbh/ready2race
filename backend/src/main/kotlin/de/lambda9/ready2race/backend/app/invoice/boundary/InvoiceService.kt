package de.lambda9.ready2race.backend.app.invoice.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.appuser.boundary.AppUserService.fullName
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.bankAccount.control.BankAccountRepo
import de.lambda9.ready2race.backend.app.bankAccount.control.PayeeBankAccountRepo
import de.lambda9.ready2race.backend.app.contactInformation.control.ContactInformationRepo
import de.lambda9.ready2race.backend.app.contactInformation.control.ContactInformationUsageRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.DocumentTemplateRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.toPdfTemplate
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentType
import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.email.entity.EmailAttachment
import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplatePlaceholder
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventRegistration.control.EventRegistrationRepo
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationError
import de.lambda9.ready2race.backend.app.invoice.control.EventRegistrationForInvoiceRepo
import de.lambda9.ready2race.backend.app.invoice.control.EventRegistrationInvoiceRepo
import de.lambda9.ready2race.backend.app.invoice.control.InvoiceDocumentDataRepo
import de.lambda9.ready2race.backend.app.invoice.control.InvoicePositionRepo
import de.lambda9.ready2race.backend.app.invoice.control.InvoiceRepo
import de.lambda9.ready2race.backend.app.invoice.control.ProduceInvoiceForRegistrationRepo
import de.lambda9.ready2race.backend.app.invoice.control.toDto
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceData
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceDto
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceError
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceForEventRegistrationSort
import de.lambda9.ready2race.backend.app.invoice.entity.ProduceInvoiceError
import de.lambda9.ready2race.backend.app.sequence.control.SequenceRepo
import de.lambda9.ready2race.backend.app.sequence.entity.SequenceConsumer
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationInvoiceRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceDocumentDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoicePositionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ProduceInvoiceForRegistrationRecord
import de.lambda9.ready2race.backend.hr
import de.lambda9.ready2race.backend.hrDate
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.ready2race.backend.pdf.FontStyle
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.PageTemplate
import de.lambda9.ready2race.backend.pdf.document
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThenNotNull
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNull
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.transact
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

object InvoiceService {

    private val logger = KotlinLogging.logger {}

    private val retryAfterError = 5.minutes

    fun page(
        params: PaginationParameters<InvoiceForEventRegistrationSort>,
    ): App<InvoiceError, ApiResponse.Page<InvoiceDto, InvoiceForEventRegistrationSort>> = KIO.comprehension {

        val total = !InvoiceRepo.count(params.search).orDie()
        val page = !InvoiceRepo.page(params).orDie()
        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total),
            )
        }
    }

    fun pageForEvent(
        id: UUID,
        params: PaginationParameters<InvoiceForEventRegistrationSort>,
    ): App<InvoiceError, ApiResponse.Page<InvoiceDto, InvoiceForEventRegistrationSort>> = KIO.comprehension {

        val total = !InvoiceRepo.countForEvent(id, params.search).orDie()
        val page = !InvoiceRepo.pageForEvent(id, params).orDie()
        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total),
            )
        }
    }

    fun pageForRegistration(
        id: UUID,
        params: PaginationParameters<InvoiceForEventRegistrationSort>,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): App<ServiceError, ApiResponse.Page<InvoiceDto, InvoiceForEventRegistrationSort>> = KIO.comprehension {
        val registrationRecord = !EventRegistrationRepo.getView(id).orDie().onNullFail {
            EventRegistrationError.NotFound
        }

        !KIO.failOn(
            scope == Privilege.Scope.OWN && registrationRecord.clubId != user.club
        ) { AuthError.PrivilegeMissing }

        val total = !InvoiceRepo.countForRegistration(id, params.search).orDie()
        val page = !InvoiceRepo.pageForRegistration(id, params).orDie()
        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total),
            )
        }

    }

    fun getDownload(
        id: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {

        // TODO: @Incomplete: not really incomplete but maybe a bug in the future, when there are different kinds of invoices

        !InvoiceRepo.getForRegistration(id).orDie().onNullFail { InvoiceError.NotFound }
            .failIf({
                scope == Privilege.Scope.OWN && it.club != user.club
            }) { AuthError.PrivilegeMissing }

        InvoiceRepo.getDownload(id).orDie().onNullDie("existence checked before").map {
            ApiResponse.File(
                name = it.filename!!,
                bytes = it.data!!
            )
        }
    }

    fun createRegistrationInvoicesForEventJobs(
        eventId: UUID,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }

        !KIO.failOn(
            event.published == true &&
                event.registrationAvailableTo?.let { it > LocalDateTime.now() } == true
        ) { InvoiceError.Registration.Ongoing }

        !KIO.failOn(
            event.invoicesProduced != null
        ) { InvoiceError.Registration.AlreadyProduced }

        val bankAccount = !PayeeBankAccountRepo.getByEvent(eventId).orDie()
            .onNull {
                PayeeBankAccountRepo.getByEvent(null).orDie()
            }
            .onNullFail { InvoiceError.MissingAssignedPayeeBankAccount }

        val contactUsage = !ContactInformationUsageRepo.getByEvent(eventId).orDie()
            .onNull {
                ContactInformationUsageRepo.getByEvent(null).orDie()
            }
            .onNullFail { InvoiceError.MissingAssignedContactInformation }

        val registrations = !EventRegistrationRepo.getIdsByEvent(eventId).orDie()

        !ProduceInvoiceForRegistrationRepo.create(
            registrations.map {
                ProduceInvoiceForRegistrationRecord(
                    eventRegistration = it,
                    contact = contactUsage.contactInformation,
                    payee = bankAccount.bankAccount,
                    createdAt = LocalDateTime.now(),
                    createdBy = userId,
                )
            }
        ).orDie()

        event.invoicesProduced = LocalDateTime.now()
        event.update()

        noData
    }

    fun produceNextRegistrationInvoice(): App<ProduceInvoiceError, Unit> = KIO.comprehension {

        val job = !ProduceInvoiceForRegistrationRepo.getAndLockNext(retryAfterError).orDie().onNullFail { ProduceInvoiceError.NoOpenJobs }

        val registration = !EventRegistrationForInvoiceRepo.get(job.eventRegistration).orDie().onNullDie("foreign key constraint")

        val recipient = registration.recipient

        if (recipient == null) {

            job.lastErrorAt = LocalDateTime.now()
            job.lastError = "missing recipient"
            job.update()

            KIO.fail(ProduceInvoiceError.MissingRecipient(registration.id!!))
        } else {
            val payee = !BankAccountRepo.get(job.payee).orDie().onNullDie("foreign key constraint")
            val contact = !ContactInformationRepo.get(job.contact).orDie().onNullDie("foreign key constraint")
            val event = !EventRepo.get(registration.event!!).orDie().onNullDie("foreign key constraint")

            KIO.comprehension {
                val seq = !SequenceRepo.getAndIncrement(SequenceConsumer.INVOICE).orDie()

                val invoiceNumber = (event.invoicePrefix ?: "") + seq.toString()

                val filename = "invoice_$invoiceNumber.pdf"

                val invoice = InvoiceRecord(
                    id = UUID.randomUUID(),
                    invoiceNumber = invoiceNumber,
                    filename = filename,
                    billedToName = recipient.fullName(),
                    billedToOrganization = registration.clubName,
                    paymentDueBy = event.paymentDueBy ?: LocalDate.now().plusDays(14),
                    payeeHolder = payee.holder,
                    payeeIban = payee.iban,
                    payeeBic = payee.bic,
                    payeeBank = payee.bank,
                    contactName = contact.name,
                    contactZip = contact.addressZip,
                    contactCity = contact.addressCity,
                    contactStreet = contact.addressStreet,
                    contactEmail = contact.email,
                    createdAt = LocalDateTime.now(),
                    createdBy = job.createdBy
                )

                val id = !InvoiceRepo.create(invoice).orDie()

                !EventRegistrationInvoiceRepo.create(
                    EventRegistrationInvoiceRecord(
                        eventRegistration = registration.id!!,
                        invoice = id,
                    )
                ).orDie()

                var position = 0
                val positions = registration.competitions!!.groupBy { it!!.propertiesId }.values.flatMap { sameCompetitions ->
                    val compRef = sameCompetitions.first()!!
                    val allFees = sameCompetitions.flatMap { competition -> competition!!.appliedFees!!.toList() }
                    allFees.groupBy { it!!.id }.values.map { sameFees ->
                        val ref = sameFees.first()!!
                        InvoicePositionRecord(
                            invoice = id,
                            position = ++position,
                            item = ref.name!!,
                            description = "${compRef.identifier} - ${compRef.name}",
                            quantity = sameFees.size.toBigDecimal(),
                            unitPrice = ref.amount!!
                        )
                    }
                }

                !InvoicePositionRepo.create(positions).orDie()

                val bytes = !generateInvoiceDocument(event, invoice, positions)

                !InvoiceDocumentDataRepo.create(
                    InvoiceDocumentDataRecord(
                        invoice = id,
                        data = bytes
                    )
                ).orDie()

                val content = !EmailService.getTemplate(
                    EmailTemplateKey.EVENT_REGISTRATION_INVOICE,
                    EmailLanguage.valueOf(recipient.language)
                ).map { mailTemplate ->
                    mailTemplate.toContent(
                        EmailTemplatePlaceholder.EVENT to event.name,
                        EmailTemplatePlaceholder.RECIPIENT to recipient.fullName(),
                        EmailTemplatePlaceholder.DATE to invoice.paymentDueBy.hr(),
                    )
                }

                !EmailService.enqueue(
                    recipient = recipient.email,
                    content = content,
                    attachments = listOf(
                        EmailAttachment(filename, bytes)
                    ),
                )

                job.delete()

                unit
            }.transact()
        }
    }

    private fun generateInvoiceDocument(
        event: EventRecord,
        invoice: InvoiceRecord,
        positions: List<InvoicePositionRecord>,
    ): App<Nothing, ByteArray> = KIO.comprehension {

        val pdfTemplate = !DocumentTemplateRepo.getAssigned(DocumentType.INVOICE, event.id).orDie()
            .andThenNotNull { it.toPdfTemplate() }

        val bytes = buildPdf(
            data = InvoiceData.fromPersisted(
                event,
                invoice,
                positions,
            ),
            template = pdfTemplate,
        )

        KIO.ok(bytes)
    }

    fun buildPdf(
        data: InvoiceData,
        template: PageTemplate?,
    ): ByteArray {
        val totalAmount = data.positions.sumOf { pos -> pos.unitPrice * pos.quantity }
        val doc = document(template) {
            page {
                table {
                    column(0.6f)
                    column(0.4f)

                    row {
                        cell {
                            block(
                                padding = Padding(top = 20f, bottom = 5f)
                            ) {
                                text(
                                    fontSize = 6f
                                ) { "${data.contact.name} – ${data.contact.street} – ${data.contact.zip} ${data.contact.city}" }
                            }
                        }
                        cell {
                            text(
                                fontSize = 15f,
                                fontStyle = FontStyle.BOLD,
                            ) { data.contact.name }
                        }
                    }

                    row {
                        cell {
                            data.billedToOrga?.let {
                                text { it }
                            }
                            text { data.billedToName }
                        }

                        cell {
                            text { data.contact.street }
                            text { "${data.contact.zip} ${data.contact.city}" }
                            text { data.contact.email }
                            text { "" }
                            text { data.createdAt.hrDate() }
                        }
                    }
                }

                block(
                    padding = Padding(top = 20f)
                ) {
                    text(
                        fontSize = 13f,
                        fontStyle = FontStyle.BOLD,
                    ) { data.eventName }
                }

                block(
                    padding = Padding(top = 10f),
                ) {
                    text(
                        fontSize = 11f,
                        fontStyle = FontStyle.BOLD,
                    ) { "Rechnungsnummer: ${data.invoiceNumber}" }
                }

                block(
                    padding = Padding(top = 20f),
                ) {
                    text { "Sehr geehrte Damen und Herren," }
                    text { "" }
                    text { "vielen Dank für die Meldung zu ${data.eventName}." }
                    text { "" }
                    text { "Für die Meldung wird ein Gesamtbetrag von $totalAmount € fällig." }
                    text { "Wir bitten um Überweisung des entsprechenden Betrags auf das nachfolgende Konto bis zum ${data.paymentDueBy.hr()}. Eine Aufschlüsselung der einzelnen Position finden Sie weiter unten." }
                    text { "" }
                    text { "" }

                    table {
                        column(0.25f)
                        column(0.75f)

                        row {
                            cell {
                                text { "Verwendungszweck:" }
                            }
                            cell {
                                text { data.invoiceNumber }
                            }
                        }

                        row {
                            cell {
                                text { "" }
                            }
                        }

                        row {
                            cell {
                                text { "Empfänger:" }
                            }
                            cell {
                                text { data.payee.holder }
                            }
                        }

                        row {
                            cell {
                                text { "IBAN:" }
                            }
                            cell {
                                text { data.payee.iban }
                            }
                        }

                        row {
                            cell {
                                text { "BIC:" }
                            }
                            cell {
                                text { data.payee.bic }
                            }
                        }

                        row {
                            cell {
                                text { "Bank:" }
                            }
                            cell {
                                text { data.payee.bank }
                            }
                        }
                    }

                    text { "" }
                    text { "Vielen Dank im Voraus." }
                    text { "" }
                    text { "" }
                    text(
                        fontStyle = FontStyle.BOLD,
                    ) { "Rechnungspositionen" }
                    text { "" }
                    table(
                        withBorder = true,
                    ) {
                        column(0.1f)
                        column(0.25f)
                        column(0.4f)
                        column(0.1f)
                        column(0.15f)

                        row(
                            color = Color(230, 230, 230)
                        ) {
                            cell {
                                text { "Pos." }
                            }
                            cell {
                                text { "Item" }
                            }
                            cell {
                                text { "Beschreibung" }
                            }
                            cell {
                                text { "Anzahl" }
                            }
                            cell {
                                text { "Einzelpreis" }
                            }
                        }

                        data.positions.map { position ->

                            row {
                                cell {
                                    text { position.position.toString() }
                                }
                                cell {
                                    text { position.item }
                                }
                                cell {
                                    text { position.description ?: "" }
                                }
                                cell {
                                    text { position.quantity.toString() }
                                }
                                cell {
                                    text { position.unitPrice.toString() }
                                }
                            }

                        }
                    }
                }
            }
        }

        val bytes = ByteArrayOutputStream().use {
            doc.save(it)
            doc.close()
            it.toByteArray()
        }

        return bytes
    }
}