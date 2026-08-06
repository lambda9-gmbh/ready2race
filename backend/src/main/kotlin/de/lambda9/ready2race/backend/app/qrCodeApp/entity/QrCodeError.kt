package de.lambda9.ready2race.backend.app.qrCodeApp.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*

enum class QrCodeError : ServiceError {

    QrCodeAlreadyInUse,

    /**
     * Toter Code: der einzige Aufruf in [de.lambda9.ready2race.backend.app.qrCodeApp.boundary.QrCodeAppService.loadQrCode]
     * ist auskommentiert - ein unbekannter Code liefert dort bewusst eine leere Antwort, damit der
     * Scanner ihn als "noch frei" behandeln kann. Bleibt vorerst stehen, bekommt aber keinen Code,
     * weil ihn niemand auslösen kann.
     */
    QrCodeNotFound;

    override fun respond(): ApiError = when (this) {
        QrCodeNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Qr Code not found")

        // Bändchen-Ausgabe: der Code hängt schon an jemand anderem. Die bisherige Meldung "Code
        // konnte nicht zugewiesen werden" klingt nach Scanfehler und schickt die Helfer dazu, es
        // noch einmal zu scannen - dabei brauchen sie ein anderes Bändchen.
        QrCodeAlreadyInUse -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "Qr Code already in use.",
            errorCode = ErrorCode.QR_CODE_ALREADY_IN_USE,
        )
    }
}
