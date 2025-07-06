package de.lambda9.ready2race.backend.app.qrCodeApp.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.HttpStatusCode

enum class QrCodeError: ServiceError {
        QrCodeNotInUse;

        override fun respond(): ApiError = when (this) {
            QrCodeNotInUse -> ApiError(status = HttpStatusCode.NotFound, message = "Qr Code not found")
        }
    }