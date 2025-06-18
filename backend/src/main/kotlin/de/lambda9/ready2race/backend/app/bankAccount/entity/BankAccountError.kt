package de.lambda9.ready2race.backend.app.bankAccount.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.HttpStatusCode

enum class BankAccountError : ServiceError {

    NotFound;

    override fun respond(): ApiError = when (this) {
        BankAccountError.NotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Bank account not found"
        )
    }
}