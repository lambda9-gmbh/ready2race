package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class CompetitionSetupMatchDto(
    val duplicatable: Boolean,
    val weighting: Int?,
    val teams: Int?,
    val name: String?,
    val participants: List<Int>,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid // todo: validate (e.g. match needs a weighting when in a round and has to be null when in a group)

    companion object {
        val example
            get() = CompetitionSetupMatchDto(
                duplicatable = false,
                weighting = 1,
                teams = 2,
                name = "Match name",
                participants = listOf(1, 8),
            )
    }
}