package de.lambda9.ready2race.backend.app.webDAV.entity.competitionTemplates

import java.time.LocalDateTime
import java.util.*

data class CompetitionTemplateExport(
    val id: UUID,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
    val updatedAt: LocalDateTime,
    val updatedBy: UUID?,
    val competitionSetupTemplate: UUID?,
)