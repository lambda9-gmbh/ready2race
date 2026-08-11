package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.util.UUID

data class ParticipantInfo(
    val participantId: UUID,
    val firstName: String,
    val lastName: String,
    /** Jahrgang - in den Ergebnis-Anzeigen hinter dem Namen (Wunsch von Lea, 10.08.2026). */
    val year: Int?,
    val namedRole: String?,
    val externalClubName: String?
)