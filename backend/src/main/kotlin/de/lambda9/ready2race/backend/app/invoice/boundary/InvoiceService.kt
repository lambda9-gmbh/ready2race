package de.lambda9.ready2race.backend.app.invoice.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.appuser.boundary.AppUserService.fullName
import de.lambda9.ready2race.backend.app.bankAccount.control.BankAccountRepo
import de.lambda9.ready2race.backend.app.bankAccount.control.PayeeBankAccountRepo
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
import de.lambda9.ready2race.backend.app.invoice.control.EventRegistrationForInvoiceRepo
import de.lambda9.ready2race.backend.app.invoice.control.EventRegistrationInvoiceRepo
import de.lambda9.ready2race.backend.app.invoice.control.InvoiceDocumentDataRepo
import de.lambda9.ready2race.backend.app.invoice.control.InvoicePositionRepo
import de.lambda9.ready2race.backend.app.invoice.control.InvoiceRepo
import de.lambda9.ready2race.backend.app.invoice.control.ProduceInvoiceForRegistrationRepo
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceError
import de.lambda9.ready2race.backend.app.invoice.entity.ProduceInvoiceError
import de.lambda9.ready2race.backend.app.sequence.control.SequenceRepo
import de.lambda9.ready2race.backend.app.sequence.entity.SequenceConsumer
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationInvoiceRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceDocumentDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoicePositionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ProduceInvoiceForRegistrationRecord
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.ready2race.backend.pdf.FontStyle
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.document
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThenNotNull
import de.lambda9.tailwind.core.extensions.kio.onNull
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.jooq.transact
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

object InvoiceService {

    private val logger = KotlinLogging.logger {}

    private val retryAfterError = 5.minutes

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

        val registrations = !EventRegistrationRepo.getIdsByEvent(eventId).orDie()

        !ProduceInvoiceForRegistrationRepo.create(
            registrations.map {
                ProduceInvoiceForRegistrationRecord(
                    eventRegistration = it,
                    contact = TODO(),
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
            val event = !EventRepo.get(registration.event!!).orDie().onNullDie("foreign key constraint")

            KIO.comprehension {
                val seq = !SequenceRepo.getAndIncrement(SequenceConsumer.INVOICE).orDie()

                val filename = "foo.pdf"

                val invoice = InvoiceRecord(
                    id = UUID.randomUUID(),
                    invoiceNumber = (event.invoicePrefix ?: "") + seq.toString(),
                    filename = filename,
                    billedToName = recipient.fullName(),
                    billedToOrganization = registration.clubName,
                    paymentDueBy = LocalDateTime.now(), // todo: use correct time from event configuration
                    payeeHolder = payee.holder,
                    payeeIban = payee.iban,
                    payeeBic = payee.bic,
                    payeeBank = payee.bank,
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
                        EmailTemplatePlaceholder.DATE to invoice.paymentDueBy.format(DateTimeFormatter.ISO_DATE_TIME),
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

        val totalAmount = positions.sumOf { pos -> pos.unitPrice * pos.quantity }

        val doc = document(pdfTemplate) {
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
                                ) { "[TODO] verein - adresse - ort" }
                            }
                        }
                        cell {
                            text(
                                fontSize = 15f,
                                fontStyle = FontStyle.BOLD,
                            ) { "[TODO] Verein" }
                        }
                    }

                    row {
                        cell {
                            invoice.billedToOrganization?.let {
                                text { it }
                            }
                            text { invoice.billedToName }
                        }

                        cell {
                            text { "[TODO] Adresse - Straße" }
                            text { "[TODO] Adresse - Ort" }
                            text { "[TODO] E-Mail" }
                            text { "" }
                            text { invoice.createdAt.format(DateTimeFormatter.ISO_DATE_TIME) }
                        }
                    }
                }

                block(
                    padding = Padding(top = 20f)
                ) {
                    text(
                        fontSize = 13f,
                        fontStyle = FontStyle.BOLD,
                    ) { event.name }
                }

                block(
                    padding = Padding(top = 10f),
                ) {
                    text(
                        fontSize = 11f,
                        fontStyle = FontStyle.BOLD,
                    ) { "Rechnungsnummer: ${invoice.invoiceNumber}" }
                }

                block(
                    padding = Padding(top = 20f),
                ) {
                    text { "Sehr geehrte Damen und Herren," }
                    text { "" }
                    text { "vielen Dank für die Meldung zu ${event.name}." }
                    text { "" }
                    text { "Für die Meldung wird ein Gesamtbetrag von $totalAmount € fällig." }
                    text { "Wir bitten um Überweisung des entsprechenden Betrags unter Angabe der Rechnungsnummer " +
                        "als Verwendungszweck auf das nachfolgende Konto bis zum ${invoice.paymentDueBy.format(
                        DateTimeFormatter.ISO_DATE)}:" }
                    text { "" }
                    text { "" }

                    table {
                        column(0.25f)
                        column(0.75f)

                        row {
                            cell {
                                text { "Empfänger:" }
                            }
                            cell {
                                text { invoice.payeeHolder }
                            }
                        }

                        row {
                            cell {
                                text { "IBAN:" }
                            }
                            cell {
                                text { invoice.payeeIban }
                            }
                        }

                        row {
                            cell {
                                text { "BIC:" }
                            }
                            cell {
                                text { invoice.payeeBic }
                            }
                        }

                        row {
                            cell {
                                text { "Bank:" }
                            }
                            cell {
                                text { invoice.payeeBank }
                            }
                        }
                    }

                    text { "" }
                    text { "Vielen Dank im Voraus." }
                }
            }
        }

        val bytes = ByteArrayOutputStream().use {
            doc.save(it)
            it.toByteArray()
        }

        KIO.ok(bytes)
    }
}