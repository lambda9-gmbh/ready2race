package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.time.LocalDateTime
import java.util.*

data class ParticipantDto(
    val id: UUID,
    val firstname: String,
    val lastname: String,
    val year: Int?,
    val gender: Gender,
    val phone: String?,
    val external: Boolean?,
    val externalClubName: String?,
    val usedInRegistration: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val email: String?,
    /** Der Stammverein. Nur er (und globales Recht) darf die Stammdaten ändern. */
    val clubId: UUID,
    val clubName: String,
    /**
     * Die weiteren Vereine, die diese Person melden dürfen (Migration V202608142000). Der
     * Stammverein steht hier nicht noch einmal drin.
     */
    val additionalClubs: List<ParticipantClubDto>,
)

/** Ein Verein, so knapp wie die Personenliste ihn braucht. */
data class ParticipantClubDto(
    val id: UUID,
    val name: String,
)
