package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection

data class UpdateCompetitionMatchResultRequest(
    val teamResults: List<UpdateCompetitionMatchTeamResultRequest>
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::teamResults validate collection,
        this::teamResults validate noDuplicates(
            UpdateCompetitionMatchTeamResultRequest::place
        )
    )

    companion object {
        val example
            get() = UpdateCompetitionMatchResultRequest(
                teamResults = listOf(UpdateCompetitionMatchTeamResultRequest.example)
            )
    }
}