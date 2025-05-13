package de.lambda9.ready2race.backend.app.competitionSetupTemplate.entity

import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupRoundDto
import java.util.*

data class CompetitionSetupTemplateDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val rounds: List<CompetitionSetupRoundDto>
)