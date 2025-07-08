package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection
import java.time.LocalDateTime

data class UpdateCompetitionMatchRequest(
    val startTime: LocalDateTime?,
    val teams: List<UpdateCompetitionMatchTeamRequest>
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::teams validate collection,
        this::teams validate noDuplicates(
            UpdateCompetitionMatchTeamRequest::startNumber
        )
    )

    companion object {
        val example
            get() = UpdateCompetitionMatchRequest(
                startTime = LocalDateTime.now(),
                teams = listOf(UpdateCompetitionMatchTeamRequest.example)
            )
    }
}