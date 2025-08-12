package de.lambda9.ready2race.backend.app.competitionRegistration.entity

import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryDto
import de.lambda9.ready2race.backend.app.competitionDeregistration.entity.CompetitionDeregistrationDto
import java.time.LocalDateTime
import java.util.*

data class CompetitionRegistrationDto(
    val id: UUID,
    val name: String?,
    val clubId: UUID,
    val clubName: String,
    val optionalFees: List<CompetitionRegistrationFeeDto>,
    val namedParticipants: List<CompetitionRegistrationNamedParticipantDto>,
    val isLate: Boolean,
    val ratingCategory: RatingCategoryDto?,
    val updatedAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val deregistration: CompetitionDeregistrationDto?,
)