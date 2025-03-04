package de.lambda9.ready2race.backend.app.competitionProperties.entity

import java.util.UUID

data class CompetitionPropertiesContainingReference(
    val competitionTemplateId: UUID?,
    val competitionId: UUID?,
    val name: String,
    val shortName: String?,
)

fun List<CompetitionPropertiesContainingReference>.splitTemplatesAndCompetitions(): CompetitionsOrTemplatesContainingReference {
    return CompetitionsOrTemplatesContainingReference(
        templates = this.filter { it.competitionTemplateId != null }.ifEmpty { null },
        competitions = this.filter { it.competitionId != null }.ifEmpty { null },
    )
}