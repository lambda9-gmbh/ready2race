package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRef
import java.util.UUID

data class CompetitionMatchTeamWithRegistration(
    val id: UUID,
    val competitionMatch: UUID,
    val startNumber: Int,
    val place: Int?,
    val timeString: String?,
    val placesCalculated: Boolean,
    val competitionRegistration: UUID,
    val clubId: UUID,
    val clubName: String,
    val registrationName: String?,
    val teamNumber: Int?,
    val participants: List<CompetitionMatchTeamParticipant>,
    val deregistered: Boolean,
    val deregistrationReason: String?,
    val out: Boolean,
    val failed: Boolean,
    val failedReason: String?,
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    /**
     * Die Wertungskategorie der Mannschaft. Trug bis zum 09.08.2026 nur den Namen; seit die
     * Platzierung je Kategorie gewertet wird, braucht sie zusätzlich die Id zum Gruppieren und
     * die Sortierstelle zum Anordnen der Abschnitte.
     */
    val ratingCategory: RatingCategoryRef?,
    val mixedTeamTerm: String?
)