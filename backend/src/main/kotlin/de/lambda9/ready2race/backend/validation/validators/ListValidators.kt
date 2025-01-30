package de.lambda9.ready2race.backend.validation.validators

import de.lambda9.ready2race.backend.app.raceProperties.entity.NamedParticipantForRaceRequestDto
import de.lambda9.ready2race.backend.validation.StructuredValidationResult

object ListValidators {
    val notEmpty
        get() = Validator<List<*>?> {
            if (it != null && it.isEmpty()) {
                StructuredValidationResult.Invalid.Message { "is empty" }
            } else {
                StructuredValidationResult.Valid
            }
        }

    val noNamedParticipantDuplicates
        get() = Validator<List<NamedParticipantForRaceRequestDto>?> { dtos ->
            if (dtos != null &&
                dtos.groupingBy { Pair(it.namedParticipant, it.required) }.eachCount().filter { it.value > 1 }
                    .isNotEmpty()
            ) {
                StructuredValidationResult.Invalid.Message { "has duplicates" }
            } else {
                StructuredValidationResult.Valid
            }
        }
}