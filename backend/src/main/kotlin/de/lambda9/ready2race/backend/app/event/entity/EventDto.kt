package de.lambda9.ready2race.backend.app.event.entity

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class EventDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val location: String?,
    val registrationAvailableFrom: LocalDateTime?,
    val registrationAvailableTo: LocalDateTime?,
    val lateRegistrationAvailableTo: LocalDateTime?,
    val invoicePrefix: String?,
    val published: Boolean?,
    val invoicesProduced: LocalDateTime?,
    val lateInvoicesProduced: LocalDateTime?,
    val paymentDueBy: LocalDate?,
    val latePaymentDueBy: LocalDate?,
    val registrationCount: Int?,
    val registrationsFinalized: Boolean,
    val mixedTeamTerm: String?,
    val challengeEvent: Boolean,
    val challengeResultType: MatchResultType?,
    val allowSelfSubmission: Boolean,
    val submissionNeedsVerification: Boolean,
    val allowParticipantSelfRegistration: Boolean,
)