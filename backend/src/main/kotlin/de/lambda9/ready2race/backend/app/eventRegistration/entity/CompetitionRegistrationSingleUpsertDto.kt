package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.util.*

data class CompetitionRegistrationSingleUpsertDto (
    val competitionId: UUID,
    val optionalFees: List<UUID>?,
)