package de.lambda9.ready2race.backend.app.eventDay.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*
import java.util.*


sealed interface EventDayError : ServiceError {
    data object EventDayNotFound : EventDayError
    data object IsChallengeEvent : EventDayError
    data object TimeslotNotFound : EventDayError
    data object CompetitionUnitAlreadyHasTimeslot : EventDayError
    data object LowerCompetitionUnitAlreadyHasTimeslot : EventDayError
    data object HigherCompetitionUnitAlreadyHasTimeslot : EventDayError

    data class CompetitionsNotFound(val competitions: List<UUID>) : EventDayError

    override fun respond(): ApiError = when (this) {
        EventDayNotFound -> ApiError(HttpStatusCode.NotFound, message = "EventDay not found")
        IsChallengeEvent -> ApiError(
            HttpStatusCode.BadRequest,
            message = "EventDays are not supported for challenge events"
        )
        TimeslotNotFound -> ApiError(HttpStatusCode.NotFound, message = "Timeslot not found")
        CompetitionUnitAlreadyHasTimeslot -> ApiError(
            HttpStatusCode.Conflict,
            message = "Competition unit already has a timeslot assigned",
            errorCode = ErrorCode.DUPLICATE_TIMESLOT
        )
        LowerCompetitionUnitAlreadyHasTimeslot -> ApiError(
            HttpStatusCode.Conflict,
            message = "Lower competition unit already has a timeslot assigned",
            errorCode = ErrorCode.CHILD_TIMESLOT_ALREADY_EXISTS
        )
        HigherCompetitionUnitAlreadyHasTimeslot -> ApiError(
            HttpStatusCode.Conflict,
            message = "Higher competition unit already has a timeslot assigned",
            errorCode = ErrorCode.PARENT_TIMESLOT_ALREADY_EXISTS
        )

        is CompetitionsNotFound -> ApiError(
            HttpStatusCode.BadRequest,
            message = "Competitions not found",
            details = mapOf("unknownIds" to competitions)
        )
    }
}
