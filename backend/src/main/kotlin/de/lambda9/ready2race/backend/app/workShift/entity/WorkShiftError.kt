package de.lambda9.ready2race.backend.app.workShift.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class WorkShiftError : ServiceError {
    NotFound,
    ASSIGNED_CLUB_REPRESENTATIVE;

    override fun respond(): ApiError = when (this) {
        NotFound ->
            ApiError(
                status = HttpStatusCode.NotFound,
                message = "WorkShift not found",
            )
        ASSIGNED_CLUB_REPRESENTATIVE -> ApiError(status = HttpStatusCode.BadRequest, message = "Assigned a club representative to the work shift")

    }
}