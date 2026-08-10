package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class ParticipantRequirementUpsertDto(
    val name: String,
    val description: String?,
    val optional: Boolean?,
    val checkInApp: Boolean?,
    val publiclyVisible: Boolean?,
    val checkEarliestMinutesBefore: Int?,
    val checkLatestMinutesBefore: Int?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::name validate notBlank,
        this::checkEarliestMinutesBefore validate IntValidators.min(1),
        this::checkLatestMinutesBefore validate IntValidators.min(1),
        if (checkEarliestMinutesBefore != null && checkLatestMinutesBefore != null
            && checkEarliestMinutesBefore <= checkLatestMinutesBefore
        ) {
            ValidationResult.Invalid.Message { "checkEarliestMinutesBefore must be greater than checkLatestMinutesBefore" }
        } else {
            ValidationResult.Valid
        },
    )

    companion object {
        val example
            get() = ParticipantRequirementUpsertDto(
                name = "Name",
                description = "Description",
                optional = false,
                checkInApp = false,
                publiclyVisible = false,
                checkEarliestMinutesBefore = 120,
                checkLatestMinutesBefore = 15,
            )
    }
}
