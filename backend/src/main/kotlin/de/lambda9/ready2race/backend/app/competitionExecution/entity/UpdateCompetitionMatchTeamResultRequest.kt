package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isNull
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isValue
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import java.util.*

data class UpdateCompetitionMatchTeamResultRequest(
    val registrationId: UUID,
    val place: Int?,
    val failed: Boolean,
    val failedReason: String?
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::place validate min(1),
        ValidationResult.oneOf(
            this::place validate notNull,
            this::failed validate isValue(true)
        ),
        ValidationResult.oneOf(
            this::failed validate isValue(true),
            ValidationResult.allOf(
                this::failed validate isValue(false),
                this::failedReason validate isNull
            ),
        ),
        this::failedReason validate notBlank,
    )

    companion object {
        val example
            get() = UpdateCompetitionMatchTeamResultRequest(
                registrationId = UUID.randomUUID(),
                place = 1,
                failed = false,
                failedReason = null
            )
    }
}