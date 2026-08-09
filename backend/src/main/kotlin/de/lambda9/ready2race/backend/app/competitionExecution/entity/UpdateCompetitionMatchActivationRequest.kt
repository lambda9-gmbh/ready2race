package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

/**
 * Ruft einen Lauf an den Start ([activated] = true) oder nimmt das zurück.
 *
 * Bewusst `activated` und nicht mehr `currentlyRunning`: Der Klick stellt fest, dass der Lauf
 * drankommt, nicht dass er fährt. Ob er fährt, entscheidet der Ist-Start (`started_at`).
 */
data class UpdateCompetitionMatchActivationRequest(
    val activated: Boolean
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example = UpdateCompetitionMatchActivationRequest(
            activated = true
        )
    }
}
