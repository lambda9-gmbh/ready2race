package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.timecodePattern
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.StringValidators.pattern
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isNull
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isValue
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import java.util.*

data class UpdateCompetitionMatchTeamResultRequest(
    val registrationId: UUID,
    val place: Int?,
    val timeString: String?,
    val failed: Boolean,
    val failedReason: String?
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::place validate min(1),
        ValidationResult.oneOf(
            ValidationResult.anyOf(
                this::place validate notNull,
                this::timeString validate notNull,
            ),
            this::failed validate isValue(true),
        ),
        ValidationResult.oneOf(
            this::failed validate isValue(true),
            ValidationResult.allOf(
                this::failed validate isValue(false),
                this::failedReason validate isNull
            ),
        ),
        this::failedReason validate notBlank,
        this::timeString validate pattern(timecodePattern)
    )

    companion object {
        val example
            get() = UpdateCompetitionMatchTeamResultRequest(
                registrationId = UUID.randomUUID(),
                place = 1,
                timeString = "12:34.567",
                failed = false,
                failedReason = null
            )
    }
}