package de.lambda9.ready2race.backend.app.eventExportBundle.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*

enum class EventExportBundleError : ServiceError {
    ItemNotFound,

    /** Das Dokument gehört nicht zu dieser Veranstaltung (oder es gibt es gar nicht). */
    DocumentNotFound,

    /** Ein Dokument steht höchstens einmal in der Mappe - der zweite Versuch ist ein Versehen. */
    DocumentAlreadyInBundle,

    /**
     * Der Platzhalter lässt sich nur verschieben, nie löschen: Ohne ihn hätte der Export keine
     * Stelle mehr, an der die generierten Startlisten stehen. Abwählen kann man ihn im
     * Export-Dialog.
     */
    PlaceholderNotRemovable,

    /**
     * Die Reihenfolge muss GENAU die aktuellen Einträge der Veranstaltung tragen - fehlt einer
     * oder ist ein fremder darunter, wäre das Ergebnis eine Mappe, die niemand so bestellt hat
     * (typisch: zwei Browser-Tabs mit verschieden altem Stand).
     */
    OrderMismatch;

    override fun respond(): ApiError = when (this) {
        ItemNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Export bundle item not found",
        )

        DocumentNotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Event document not found for this event",
        )

        DocumentAlreadyInBundle -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "The document is already part of the export bundle",
            errorCode = ErrorCode.EXPORT_BUNDLE_DUPLICATE_DOCUMENT,
        )

        PlaceholderNotRemovable -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The generated-startlists placeholder cannot be removed, only reordered",
            errorCode = ErrorCode.EXPORT_BUNDLE_PLACEHOLDER_NOT_REMOVABLE,
        )

        OrderMismatch -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "The submitted order does not contain exactly the current bundle items",
            errorCode = ErrorCode.EXPORT_BUNDLE_ORDER_MISMATCH,
        )
    }
}
