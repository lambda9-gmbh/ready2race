package de.lambda9.ready2race.backend.app.event.entity

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class EventPublicDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val location: String?,
    val registrationAvailableFrom: LocalDateTime?,
    val registrationAvailableTo: LocalDateTime?,
    val lateRegistrationAvailableTo: LocalDateTime?,
    val createdAt: LocalDateTime,
    val competitionCount: Long,
    val eventFrom: LocalDate?,
    val eventTo: LocalDate?,
    val challengeEvent: Boolean,
    val challengeResultType: MatchResultType?,
    val allowSelfSubmission: Boolean,
    val allowParticipantSelfRegistration: Boolean,
)