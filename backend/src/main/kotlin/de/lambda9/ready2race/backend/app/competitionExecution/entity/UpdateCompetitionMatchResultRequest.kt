package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection

data class UpdateCompetitionMatchResultRequest(
    val teamResults: List<UpdateCompetitionMatchTeamResultRequest>
): Validatable {
    override fun validate(): ValidationResult = this::teamResults validate collection

    companion object {
        val example get() = UpdateCompetitionMatchResultRequest(
            teamResults = listOf(UpdateCompetitionMatchTeamResultRequest.example)
        )
    }
}
// todo: validate that place is max teamResults.count and every place is unique