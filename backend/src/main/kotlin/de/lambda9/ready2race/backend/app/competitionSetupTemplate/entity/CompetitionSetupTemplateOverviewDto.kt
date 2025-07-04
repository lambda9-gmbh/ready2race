package de.lambda9.ready2race.backend.app.competitionSetupTemplate.entity

import java.util.*

data class CompetitionSetupTemplateOverviewDto(
    val id: UUID,
    val name: String,
    val description: String?,
)