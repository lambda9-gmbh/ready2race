package de.lambda9.ready2race.backend.app.club.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import io.ktor.http.*

enum class ClubShortNameError : ServiceError {
    /**
     * Schlüssel im Pfad und mitgeschickte Schreibweise gehören nicht zusammen. Der Eintrag würde
     * unter einem Schlüssel landen, den keine Anzeige je nachschlägt - er wäre wirkungslos und auf
     * der Pflegeseite trotzdem als "gepflegt" zu sehen.
     */
    NameKeyMismatch;

    override fun respond(): ApiError = when (this) {
        NameKeyMismatch -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Name key does not match the submitted club name",
        )
    }
}
