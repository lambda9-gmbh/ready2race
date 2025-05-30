package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection

data class CompetitionSetupGroupDto(
    val weighting: Int,
    val teams: Int?,
    val name: String?,
    val matches: List<CompetitionSetupMatchDto>,
    val participants: List<Int>,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::weighting validate min(1),
        this::teams validate min(1),
        this::name validate notBlank,
        this::participants validate collection(min(1)),
    )

    companion object {
        val example
            get() = CompetitionSetupGroupDto(
                weighting = 1,
                teams = 4,
                name = "Group name",
                matches = listOf(CompetitionSetupMatchDto.example),
                participants = listOf(1, 8, 9, 16),
            )
    }
}