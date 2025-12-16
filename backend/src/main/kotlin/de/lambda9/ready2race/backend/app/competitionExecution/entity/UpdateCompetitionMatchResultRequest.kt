package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.anyOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isNull
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isValue
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.oneOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.select

data class UpdateCompetitionMatchResultRequest(
    val teamResults: List<UpdateCompetitionMatchTeamResultRequest>
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::teamResults validate collection,
        this::teamResults validate noDuplicates(
            UpdateCompetitionMatchTeamResultRequest::registrationId
        ),
        // Either all places are set or none (if not failed)
        this::teamResults validate anyOf(
            collection(
                oneOf(
                    select(notNull,UpdateCompetitionMatchTeamResultRequest::place),
                    select(isValue(true),UpdateCompetitionMatchTeamResultRequest::failed)
                )
            ),
            collection(
                    select(isNull,UpdateCompetitionMatchTeamResultRequest::place)
            ),
        ),
        // Either all timeStrings are set or none (if not failed)
        this::teamResults validate anyOf(
            collection(
                oneOf(
                    select(notNull,UpdateCompetitionMatchTeamResultRequest::timeString),
                    select(isValue(true),UpdateCompetitionMatchTeamResultRequest::failed)
                )
            ),
            collection(
                    select(isNull,UpdateCompetitionMatchTeamResultRequest::timeString)
            ),
        )
        /* TODO: The implementation sees multiple nulls as duplicates -> Make that not happen
        this::teamResults validate noDuplicates(
            UpdateCompetitionMatchTeamResultRequest::place
        ),*/
    )

    companion object {
        val example
            get() = UpdateCompetitionMatchResultRequest(
                teamResults = listOf(UpdateCompetitionMatchTeamResultRequest.example)
            )
    }
}