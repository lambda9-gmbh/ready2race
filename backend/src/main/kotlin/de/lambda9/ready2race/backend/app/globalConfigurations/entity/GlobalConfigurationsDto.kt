package de.lambda9.ready2race.backend.app.globalConfigurations.entity

import java.time.LocalDateTime
import java.util.UUID

data class GlobalConfigurationsDto(
    val allowClubCreationOnRegistration: Boolean,
    val updatedAt: LocalDateTime,
    val updatedBy: UUID?,
)