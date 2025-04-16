package de.lambda9.ready2race.backend.app.competitionSetupTemplate.entity

import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupRoundDto
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class CompetitionSetupTemplateRequest(
    val name: String,
    val description: String?,
    val rounds: List<CompetitionSetupRoundDto>
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: validate

    companion object {
        val example
            get() = CompetitionSetupTemplateRequest(
                name = "Tournament",
                description = "Example Tournament with one Round",
                rounds = listOf(CompetitionSetupRoundDto.example),
            )
    }
}