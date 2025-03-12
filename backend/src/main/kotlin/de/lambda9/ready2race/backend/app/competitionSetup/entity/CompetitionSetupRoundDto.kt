package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class CompetitionSetupRoundDto(
    val name: String,
    val required: Boolean,
    val matches: List<CompetitionSetupMatchDto>
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: validate

    companion object {
        val example
            get() = CompetitionSetupRoundDto(
                name = "Round name",
                required = false,
                matches = listOf(CompetitionSetupMatchDto.example),
            )
    }
}