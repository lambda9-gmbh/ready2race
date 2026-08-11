package de.lambda9.ready2race.backend.app.raceclocker.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*

sealed interface RaceClockerRaceError : ServiceError {

    data object NotFound : RaceClockerRaceError

    /** Zwei Rennen gleichen Namens wären in einem Auswahlfeld nicht unterscheidbar. */
    data object NameTaken : RaceClockerRaceError

    /**
     * Zwei Rennen mit derselben Adresse wären zwei Abrufe für dieselbe Antwort — genau die
     * Verschwendung, die diese Änderung beseitigt.
     */
    data object UrlTaken : RaceClockerRaceError

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "RaceClocker race not found",
        )

        NameTaken -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "A RaceClocker race with this name already exists for this event",
            errorCode = ErrorCode.RACECLOCKER_RACE_NAME_TAKEN,
        )

        UrlTaken -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "A RaceClocker race with this results URL already exists for this event",
            errorCode = ErrorCode.RACECLOCKER_RACE_URL_TAKEN,
        )
    }
}
