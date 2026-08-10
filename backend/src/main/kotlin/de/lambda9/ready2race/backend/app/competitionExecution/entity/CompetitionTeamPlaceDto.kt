package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRef
import java.util.UUID

data class CompetitionTeamPlaceDto(
    val competitionRegistrationId: UUID,
    val teamNumber: Int,
    val teamName: String?,
    val clubId: UUID,
    val clubName: String,
    val actualClubName: String?,
    val namedParticipants: List<CompetitionTeamNamedParticipantDto>,
    /** Der wettkampfweite Platz aus der Rundenlogik. Er trägt weiterhin die Urkunde. */
    val place: Int,
    /** Die Wertungskategorie der Mannschaft, `null` ohne Zuordnung. */
    val ratingCategory: RatingCategoryRef?,
    /**
     * Der Platz innerhalb der Wertungskategorie, ab 1 — das ist die Zahl in der
     * Platzierungsansicht und im Ergebnis-PDF. `null` für ausgeschiedene und abgemeldete
     * Mannschaften.
     */
    val categoryPlace: Int?,
    val deregistered: Boolean,
    val deregistrationReason: String?,
    val excluded: Boolean,
)