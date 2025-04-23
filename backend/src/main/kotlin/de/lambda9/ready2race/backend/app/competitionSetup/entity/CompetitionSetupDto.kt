package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection

data class CompetitionSetupDto(
    val rounds: List<CompetitionSetupRoundDto>
) : Validatable {
    override fun validate(): ValidationResult = this::rounds validate allOf(
        collection,
    )

    /*todo validation:
        - if one round has unlimited teams (a match or group has teams == undefined OR a match or group is duplicatable) the next round can't have unlimited teams
        - ? First round needs to have unlimited teams
    */

    companion object {
        val example
            get() = CompetitionSetupDto(
                rounds = listOf(CompetitionSetupRoundDto.example),
            )
    }
}