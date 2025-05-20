package de.lambda9.ready2race.backend.app.invoice.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.appuser.boundary.AppUserService.fullName
import de.lambda9.ready2race.backend.app.bankAccount.control.BankAccountRepo
import de.lambda9.ready2race.backend.app.bankAccount.control.PayeeBankAccountRepo
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventRegistration.control.EventRegistrationRepo
import de.lambda9.ready2race.backend.app.invoice.control.EventRegistrationForInvoiceRepo
import de.lambda9.ready2race.backend.app.invoice.control.InvoiceRepo
import de.lambda9.ready2race.backend.app.invoice.control.ProduceInvoiceForRegistrationRepo
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceError
import de.lambda9.ready2race.backend.app.invoice.entity.ProduceInvoiceError
import de.lambda9.ready2race.backend.app.sequence.control.SequenceRepo
import de.lambda9.ready2race.backend.app.sequence.entity.SequenceConsumer
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoicePositionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.InvoiceRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ProduceInvoiceForRegistrationRecord
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.onNull
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

object InvoiceService {

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
            KIO.fail(ProduceInvoiceError.MissingRecipient(registration.id!!))
        } else {
            val payee = !BankAccountRepo.get(job.payee).orDie().onNullDie("foreign key constraint")

            val seq = !SequenceRepo.getAndIncrement(SequenceConsumer.INVOICE).orDie()

            val filename = "foo.pdf"

            val invoice = InvoiceRecord(
                id = UUID.randomUUID(),
                invoiceNumber = seq.toString(), // todo: add prefix
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

            // TODO: positions

            // TODO: document

            unit
        }

    }
}