package de.lambda9.ready2race.backend.app.competitionSetupTemplate.entity

import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupRoundDto
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.notEmpty
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection

data class CompetitionSetupTemplateRequest(
    val name: String,
    val description: String?,
    val rounds: List<CompetitionSetupRoundDto>
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::name validate notBlank,
        this::description validate notBlank,
        this::rounds validate collection
    )

    companion object {
        val example
            get() = CompetitionSetupTemplateRequest(
                name = "Tournament",
                description = "Example Tournament with one Round",
                rounds = listOf(CompetitionSetupRoundDto.example),
            )
    }
}