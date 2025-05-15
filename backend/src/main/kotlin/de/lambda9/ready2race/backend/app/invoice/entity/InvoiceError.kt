package de.lambda9.ready2race.backend.app.invoice.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.HttpStatusCode

sealed interface InvoiceError : ServiceError {

    enum class Registration : InvoiceError {

        Ongoing,
        AlreadyProduced;

        override fun respond(): ApiError = when (this) {
            Ongoing ->
                ApiError(
                    status = HttpStatusCode.Conflict,
                    message = "Event registration is still ongoing",
                    errorCode = ErrorCode.EVENT_REGISTRATION_ONGOING,
                )
            AlreadyProduced ->
                ApiError(
                    status = HttpStatusCode.Conflict,
                    message = "Invoices are already produced",
                    errorCode = ErrorCode.INVOICES_ALREADY_PRODUCED,
                )
        }
    }

    data object MissingAssignedPayeeBankAccount : InvoiceError {
        override fun respond(): ApiError = ApiError(
            status = HttpStatusCode.Conflict,
            message = "No bank account assigned as payee information for this event or globally",
            errorCode = ErrorCode.NO_ASSIGNED_PAYEE_INFORMATION,
        )
    }
}