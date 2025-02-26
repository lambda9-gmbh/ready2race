package de.lambda9.ready2race.backend.app.competition.entity

import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesRequestDto
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.Validators.Companion.notNull
import java.util.*

data class CompetitionRequest (
    val properties: CompetitionPropertiesRequestDto?,
    val template: UUID?,
): Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::properties.validate(),
            ValidationResult.oneOf(
                this::properties validate notNull,
                this::template validate notNull,
            )
        )


    companion object{
        val example get() = CompetitionRequest(
            properties = CompetitionPropertiesRequestDto.example,
            template = null, // todo: should provide 2 examples (one with properties, one with template) or extra details/description
        )
    }
}