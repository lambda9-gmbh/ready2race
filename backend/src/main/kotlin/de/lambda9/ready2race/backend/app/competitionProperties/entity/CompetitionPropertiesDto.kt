package de.lambda9.ready2race.backend.app.competitionProperties.entity

import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategoryDto
import java.math.BigDecimal

data class CompetitionPropertiesDto(
    val identifier: String,
    val name: String,
    val shortName: String?,
    val description: String?,
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int,
    val competitionCategory: CompetitionCategoryDto?,
    val namedParticipants: List<NamedParticipantForCompetitionDto>
)