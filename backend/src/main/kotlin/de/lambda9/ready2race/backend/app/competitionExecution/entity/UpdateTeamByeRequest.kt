package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import java.util.UUID

/**
 * Vergibt einem Boot ein Freilos für diesen Lauf - es kommt ohne Start in die Folgerunde.
 *
 * Der Anlass ist der Regattatag: Ein Boot verpasst seinen Vorlauf, die Schiedsrichter setzen es
 * trotzdem in den nächsten Durchgang. Der bisherige Behelf war, ihm im Vorlauf von Hand einen
 * Platz einzutragen - der stand dann im Ergebnis, und alle tatsächlich gefahrenen Boote rutschten
 * eine Position nach hinten.
 */
data class UpdateTeamByeRequest(
    val registrationId: UUID,
    val bye: Boolean,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::registrationId validate notNull,
        this::bye validate notNull,
    )

    companion object {
        val example
            get() = UpdateTeamByeRequest(
                registrationId = UUID.randomUUID(),
                bye = true,
            )
    }
}
