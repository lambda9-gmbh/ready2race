package de.lambda9.ready2race.backend.app.matchResultImportConfig.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import kotlin.String

data class MatchResultImportConfigRequest(
    val name: String,
    val colTeamStartNumber: String,
    val colTeamPlace: String,
    val colTeamTime: String,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::name validate notBlank,
            this::colTeamStartNumber validate notBlank,
            this::colTeamPlace validate notBlank,
        )

    companion object {

        val example get() = MatchResultImportConfigRequest(
            name = "Einzelrennen",
            colTeamStartNumber = "Start Number",
            colTeamPlace = "Place",
            colTeamTime = "2:31",
        )
    }
}
