package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min

/**
 * Per-participant-count override for a match's name and execution order within a round.
 * [participantCount] is the fixed bracket size N (number of teams qualifying out of the qualification round(s)).
 * [matchWeighting] identifies the match within the round (stable across edits). Only deviations from the default
 * (competition_setup_match.name / execution_order) are stored.
 */
data class CompetitionSetupMatchNamingDto(
    val participantCount: Int,
    val matchWeighting: Int,
    val name: String?,
    val executionOrder: Int?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::participantCount validate min(1),
        this::matchWeighting validate min(1),
    )

    companion object {
        val example
            get() = CompetitionSetupMatchNamingDto(
                participantCount = 10,
                matchWeighting = 1,
                name = "AF-1",
                executionOrder = 1,
            )
    }
}