package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.time.LocalDateTime
import java.util.UUID

/**
 * Das persönliche Dashboard eines Teilnehmenden, erreichbar über den QR-Code am Band.
 *
 * Bewusst nicht enthalten: E-Mail-Adresse, Geschlecht, Jahrgang und die Freitext-Notiz zu
 * einer Bedingung. Die ersten drei braucht die Ansicht nicht, die Notiz ist für interne
 * Augen geschrieben. Siehe docs/superpowers/specs/2026-08-09-mein-event-design.md.
 */
data class MyEventDto(
    val displayName: String,
    val clubName: String?,
    val eventName: String,
    val serverTime: LocalDateTime,
    val refreshIntervalSeconds: Int,
    val running: List<MyEventMatchDto>,
    val upcoming: List<MyEventMatchDto>,
    val results: List<MyEventResultDto>,
    val unscheduled: List<MyEventRegistrationDto>,
    val requirements: List<MyEventRequirementDto>,
)

data class MyEventMatchDto(
    val matchId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val startTime: LocalDateTime?,
    val actualStartTime: LocalDateTime?,
    val startState: AthleteBoardStartState,
    val lane: Int?,
    val teamName: String?,
    val clubName: String?,
    val teamMembers: List<MyEventTeamMemberDto>,
    /**
     * Zurückgezogen. Solange der Lauf noch kein öffentliches Ergebnis ist (bei
     * `FINISHED_ONLY` bis zum Beenden, unter Umständen bis zum nächsten Tag), steht die
     * Abmeldung nur hier — ohne dieses Feld sähe sie auf dem Telefon wie ein ganz normaler
     * kommender Lauf aus und schickte jemanden an den Start, der längst abgemeldet ist.
     */
    val deregistered: Boolean,
    val deregisteredReason: String?,
)

data class MyEventTeamMemberDto(
    val name: String,
    val role: String?,
    /** true für die Person, der dieser QR-Code gehört — die Anzeige hebt sie hervor. */
    val self: Boolean,
)

data class MyEventResultDto(
    val matchId: UUID,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    val startTime: LocalDateTime?,
    val actualStartTime: LocalDateTime?,
    val place: Int?,
    val timeString: String?,
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    val failed: Boolean,
    val failedReason: String?,
    val deregistered: Boolean,
    val deregisteredReason: String?,
)

data class MyEventRegistrationDto(
    val competitionId: UUID,
    val competitionIdentifier: String,
    val competitionName: String,
    val categoryName: String?,
    val teamName: String?,
    val role: String?,
)

data class MyEventRequirementDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val optional: Boolean,
    val fulfilled: Boolean,
)
