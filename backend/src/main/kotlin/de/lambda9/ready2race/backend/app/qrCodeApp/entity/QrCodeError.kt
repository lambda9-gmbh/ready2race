package de.lambda9.ready2race.backend.app.qrCodeApp.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class QrCodeError : ServiceError {

    QrCodeAlreadyInUse,
    QrCodeNotInUse;

    override fun respond(): ApiError = when (this) {
        QrCodeNotInUse -> ApiError(status = HttpStatusCode.NotFound, message = "Qr Code not found")
        QrCodeAlreadyInUse -> ApiError(status = HttpStatusCode.Conflict, message = "Qr Code already in use.")
    }
}