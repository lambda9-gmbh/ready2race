package de.lambda9.ready2race.backend.app.liveDashboard.entity

import java.time.LocalDateTime
import java.util.UUID

/**
 * Eine Schiedsrichter-Notiz zu einem Boot in einem Lauf ("Boje berührt") - Kommunikation zwischen
 * Schiedsrichtern über das Live-Dashboard, keine Wertung. Bewusst getrennt vom Ausscheidungsgrund
 * (`failedReason`) und den Notizen der Teilnahmebedingungen.
 *
 * Einträge sind unveränderlich (append-only, siehe Migration `V202608111400__match_team_notes`):
 * eine Korrektur ist Löschen + neu anlegen.
 */
data class MatchTeamNoteDto(
    val id: UUID,
    val note: String,
    val createdAt: LocalDateTime,
    /** Anzeigename der Autorin aus `app_user` - null, wenn das Konto gelöscht wurde. */
    val author: String?,
)
