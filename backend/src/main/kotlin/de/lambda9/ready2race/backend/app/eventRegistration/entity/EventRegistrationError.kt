package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.calls.responses.ApiError
import de.lambda9.ready2race.backend.calls.responses.ErrorCode
import io.ktor.http.*
import java.util.*

sealed interface EventRegistrationError : ServiceError {
    data object NotFound : EventRegistrationError
    data object EventNotFound : EventRegistrationError
    data object RegistrationClosed : EventRegistrationError
    data object RegistrationsNotFinalized : EventRegistrationError

    data class InvalidRegistration(val msg: String) : EventRegistrationError
    data class UpsertParticipantNotFound(val id: UUID) : EventRegistrationError
    data class TeamParticipantNotFound(
        val participantId: UUID,
        val namedParticipantId: UUID,
        val competitionName: String,
    ) : EventRegistrationError

    data class CompetitionNotFound(val id: UUID) : EventRegistrationError
    data class NamedParticipantNotFound(val id: UUID, val competitionName: String) : EventRegistrationError
    data class FeeNotFound(val id: UUID, val competitionName: String) : EventRegistrationError
    data class RatingCategoryNotFound(val id: UUID, val competitionName: String) : EventRegistrationError
    data class RatingCategoryMissing(val teamName: String, val competitionName: String) : EventRegistrationError
    data class AgeRequirementNotMet(val participantName: String?, val teamName: String?, val competitionName: String) :
        EventRegistrationError

    data class ParticipantDuplicateInCompetition(val participantIds: Set<UUID>, val competitionName: String) :
        EventRegistrationError

    data class InvalidTeamDistribution(val namedParticipantId: UUID, val competitionName: String) :
        EventRegistrationError

    override fun respond(): ApiError = when (this) {
        NotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Registration not Found")
        is InvalidRegistration -> ApiError(status = HttpStatusCode.BadRequest, message = "Invalid registration: $msg")
        EventNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Event not found")

        RegistrationClosed -> ApiError(
            status = HttpStatusCode.Forbidden,
            message = "Registration closed",
            errorCode = ErrorCode.EVENT_REGISTRATION_CLOSED
        )

        is UpsertParticipantNotFound -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Participant with id: $id was not found",
            errorCode = ErrorCode.EVENT_REGISTRATION_UPSERT_PARTICIPANT_NOT_FOUND,
        )

        is TeamParticipantNotFound -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Participant with id: $participantId was not found. Competition name: $competitionName, named participant id: $namedParticipantId",
            errorCode = ErrorCode.EVENT_REGISTRATION_TEAM_PARTICIPANT_NOT_FOUND,
        )

        is CompetitionNotFound -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Competition with id: $id was not found",
            errorCode = ErrorCode.EVENT_REGISTRATION_COMPETITION_NOT_FOUND
        )

        is NamedParticipantNotFound -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Named participant with id: $id was not found in competition $competitionName",
            errorCode = ErrorCode.EVENT_REGISTRATION_NAMED_PARTICIPANT_NOT_FOUND
        )

        is FeeNotFound -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Fee with id: $id was not found in competition $competitionName",
            errorCode = ErrorCode.EVENT_REGISTRATION_FEE_NOT_FOUND
        )

        is RatingCategoryNotFound -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Rating category with id: $id was not found in competition $competitionName",
            errorCode = ErrorCode.EVENT_REGISTRATION_RATING_CATEGORY_NOT_FOUND
        )

        is RatingCategoryMissing -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "Rating category not provided for $teamName in competition: $competitionName. This competition requires a rating category.",
            errorCode = ErrorCode.EVENT_REGISTRATION_RATING_CATEGORY_MISSING
        )

        is AgeRequirementNotMet -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The ${if (participantName != null) "participant $participantName" else "team $teamName"} does not meet the age requirement of their rating category in competition $competitionName.",
            errorCode = ErrorCode.EVENT_REGISTRATION_AGE_REQUIREMENT_NOT_MET
        )

        is ParticipantDuplicateInCompetition -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "${
                if (participantIds.size > 1) {
                    "Participants with ids ${participantIds.joinToString { "," }} are"
                } else {
                    "Participant with id ${participantIds.firstOrNull()} is"
                }
            } present multiple times in competition $competitionName.",
            errorCode = ErrorCode.EVENT_REGISTRATION_PARTICIPANT_DUPLICATE_IN_COMPETITION
        )

        is InvalidTeamDistribution -> ApiError(
            status = HttpStatusCode.BadRequest,
            message = "The team distribution of named participant id: $namedParticipantId in competition $competitionName is invalid. Check the number of participants in this role and their correct genders"
        )

        RegistrationsNotFinalized -> ApiError(status = HttpStatusCode.BadRequest, message = "Event not finalized")
    }
}