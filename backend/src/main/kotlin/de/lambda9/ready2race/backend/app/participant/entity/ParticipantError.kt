package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*

sealed interface ParticipantError : ServiceError {
    data object ParticipantInUse : ParticipantError
    data object ParticipantNotFound : ParticipantError

    /**
     * Der Stammverein steht schon in `participant.club` — ihn zusätzlich als "weiteren Verein"
     * einzutragen, wäre eine zweite Wahrheit über dieselbe Zugehörigkeit. Die Datenbank kann das
     * nicht prüfen (die Spalte liegt in einer anderen Tabelle), also prüft es der Service.
     */
    data object ClubIsHomeClub : ParticipantError

    /** Derselbe Verein ein zweites Mal; der Primärschlüssel würde es ohnehin abweisen. */
    data object ClubAlreadyAdded : ParticipantError

    /** Die Zugehörigkeit, die entfernt werden sollte, gibt es nicht (mehr). */
    data object ClubNotAdded : ParticipantError

    sealed interface ImportError : ParticipantError {
        data class UnknownGenderValue(val value: String) : ImportError
    }

    override fun respond(): ApiError = when (this) {
        ParticipantInUse -> ApiError(status = HttpStatusCode.Forbidden, message = "Participant can not be deleted")
        ParticipantNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Participant not found")
        ClubIsHomeClub -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "Club is already the home club of this participant",
        )
        ClubAlreadyAdded -> ApiError(
            status = HttpStatusCode.Conflict,
            message = "Club is already an additional club of this participant",
        )
        ClubNotAdded -> ApiError(
            status = HttpStatusCode.NotFound,
            message = "Club is not an additional club of this participant",
        )
        // Der beanstandete Wert reist mit: ohne ihn steht in der Oberfläche "unbekannter Wert für
        // das Geschlecht" und der Nutzer sucht ihn in einer Datei mit hunderten Zeilen selbst.
        is ImportError.UnknownGenderValue -> ApiError(
            status = HttpStatusCode.UnprocessableEntity,
            message = "Unknown gender value '$value'",
            details = mapOf("value" to value),
            errorCode = ErrorCode.PARTICIPANT_IMPORT_UNKNOWN_GENDER_VALUE,
        )
    }
}