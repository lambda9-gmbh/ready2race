package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.util.*

data class CompetitionRegistrationSingleLockedDto(
    val competitionId: UUID,
    val optionalFees: List<UUID>,
    val isLate: Boolean,
    val ratingCategory: UUID?,
)
