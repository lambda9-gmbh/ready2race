package de.lambda9.ready2race.backend.app.qrCodeApp.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*

enum class QrCodeError : ServiceError {

    QrCodeAlreadyInUse,

    /**
     * Ausgelöst beim Entfernen eines Bändchens in der Nutzerverwaltung
     * ([de.lambda9.ready2race.backend.app.appUserWithQrCode.boundary.AppUserWithQrCodeService.deleteQrCode]):
     * löscht die Abfrage keine Zeile, ist der Code unbekannt oder schon entfernt.
     *
     * Bewusst NICHT im Scanner-Weg: [de.lambda9.ready2race.backend.app.qrCodeApp.boundary.QrCodeAppService.loadQrCode]
     * liefert für einen unbekannten Code eine leere Antwort, damit der Scanner ihn als "noch frei"
     * behandeln kann, und [de.lambda9.ready2race.backend.app.qrCodeApp.boundary.QrCodeAppService.deleteQrCode]
     * löscht dort weiterhin idempotent.
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
