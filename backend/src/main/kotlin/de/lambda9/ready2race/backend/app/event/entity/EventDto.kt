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
    val hasLateRegistrations: Boolean,
    val invoicePrefix: String?,
    val published: Boolean,
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
    /** Steuert, wer Läufe beenden/aktivieren darf und ob die Kette dabei automatisch weiterzieht. */
    val chainProgressionMode: ChainProgressionMode,
    /** Zeigt Pausen/Programmpunkte aus dem Zeitplan auch auf Kiosk und Athleten-Anzeige. */
    val showBreaksOnPublicBoards: Boolean,
    /** Ab welchem Zustand ein Lauf als Ergebnis auf den öffentlichen Ansichten erscheint. */
    val publicResultsVisibility: PublicResultsVisibility,
    /** Ob die Durchführungsseite ihren Stand im Hintergrund nachzieht. */
    val executionAutoRefresh: Boolean,
    /** Takt dieses Abgleichs in Sekunden; nur wirksam, wenn [executionAutoRefresh] gesetzt ist. */
    val executionAutoRefreshSeconds: Int,
    val challengesFinished: Boolean?,
)