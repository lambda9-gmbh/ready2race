package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.time.LocalDateTime
import java.util.UUID

/**
 * Das persönliche Dashboard eines Teilnehmenden, erreichbar über den QR-Code am Band.
 *
 * Bewusst nicht enthalten: E-Mail-Adresse, Geschlecht, Jahrgang, die Freitext-Notiz zu
 * einer Bedingung sowie deren interne Beschreibung (`description` — an ihrer Stelle steht
 * der öffentliche Text `publicNote`, siehe [MyEventRequirementDto]). Die ersten drei braucht
 * die Ansicht nicht, Notiz und Beschreibung sind für interne Augen geschrieben. Siehe
 * docs/superpowers/specs/2026-08-09-mein-event-design.md.
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
    /**
     * Die Meldung des eigenen Bootes — derselbe Schlüssel wie `teamId` in den Mannschaften
     * von `/latest-match-results`. Darüber markiert "Mein Event" das eigene Boot, wenn es
     * das komplette Feld des Laufs nachlädt.
     */
    val teamId: UUID?,
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
    /**
     * Der athletengerechte, öffentliche Text zur Bedingung (Spalte `public_note`, Migration
     * V202608111600). Die interne `description` ist eine Arbeitsanweisung für die Meldestelle
     * und wird seit dem 11.08.2026 bewusst nicht mehr ausgeliefert — wer hier einen Text
     * ergänzt, ergänzt ihn für die Öffentlichkeit.
     */
    val publicNote: String?,
    val optional: Boolean,
    val fulfilled: Boolean,
    /**
     * Ab wann die Bedingung erledigt werden kann: erster künftiger Start der Person minus
     * `check_earliest_minutes_before`. null, wenn die Bedingung kein Fenster trägt oder kein
     * künftiger Start bekannt ist — dann zeigt die Anzeige keine Zeile.
     */
    val checkFrom: LocalDateTime?,
    /** Bis wann, analog aus `check_latest_minutes_before`. */
    val checkUntil: LocalDateTime?,
)
