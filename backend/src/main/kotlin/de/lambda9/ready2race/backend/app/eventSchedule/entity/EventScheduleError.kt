package de.lambda9.ready2race.backend.app.eventSchedule.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*
import java.util.UUID

sealed interface EventScheduleError : ServiceError {
    data class EventNotFound(val eventId: UUID) : EventScheduleError
    data class SlotNotFound(val slotId: UUID) : EventScheduleError
    data class SetupMatchNotFound(val setupMatchId: UUID) : EventScheduleError
    data class SetupMatchAlreadyPlanned(val setupMatchId: UUID) : EventScheduleError
    data class MatchAlreadyStarted(val slotId: UUID) : EventScheduleError
    data class SlotNotSkippable(val slotId: UUID) : EventScheduleError
    data class CompressionImpossible(val maxReductionMinutes: Long) : EventScheduleError
    data object InvalidShiftRequest : EventScheduleError
    data class DuplicateImportRow(val rowNumbers: List<Int>) : EventScheduleError
    data object ImportFileUnreadable : EventScheduleError

    override fun respond(): ApiError = when (this) {
        is EventNotFound -> ApiError(HttpStatusCode.NotFound, "Event with id $eventId not found")
        is SlotNotFound -> ApiError(HttpStatusCode.NotFound, "Schedule slot $slotId not found")
        is SetupMatchNotFound -> ApiError(HttpStatusCode.NotFound, "Setup match $setupMatchId not found in this event")
        is SetupMatchAlreadyPlanned -> ApiError(HttpStatusCode.Conflict, "Setup match $setupMatchId already has a schedule slot")
        is MatchAlreadyStarted -> ApiError(HttpStatusCode.Conflict, "The match of slot $slotId has already started")
        is SlotNotSkippable -> ApiError(HttpStatusCode.Conflict, "Slot $slotId cannot be skipped in its current state")
        is CompressionImpossible -> ApiError(
            HttpStatusCode.UnprocessableEntity,
            "Cannot compress: only $maxReductionMinutes minutes available",
            // Maschinenlesbar zusätzlich zum Freitext, damit das Frontend die Minutenzahl nicht mehr
            // aus der (übersetzbaren/änderbaren) Nachricht herausparsen muss (siehe common.ts,
            // parseMaxReductionMinutes/extractMaxReductionMinutes).
            details = mapOf("maxReductionMinutes" to maxReductionMinutes),
        )
        InvalidShiftRequest -> ApiError(HttpStatusCode.UnprocessableEntity, "Shift request parameters are inconsistent")
        is DuplicateImportRow -> ApiError(HttpStatusCode.UnprocessableEntity, "Import contains duplicate matches in rows $rowNumbers")
        ImportFileUnreadable -> ApiError(HttpStatusCode.UnprocessableEntity, "Import file could not be read")
    }
}
