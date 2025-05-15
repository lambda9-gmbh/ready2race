package de.lambda9.ready2race.backend.app.invoice.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.bankAccount.control.PayeeBankAccountRepo
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventRegistration.control.EventRegistrationRepo
import de.lambda9.ready2race.backend.app.invoice.control.ProduceInvoiceForRegistrationRepo
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceError
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.ProduceInvoiceForRegistrationRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNull
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.UUID

object InvoiceService {

    fun produceRegistrationInvoicesForEvent(
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
}