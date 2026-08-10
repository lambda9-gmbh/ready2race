package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

/**
 * Wie dieser Wettkampf zur Folgerunden-Automatik steht — und was daraus tatsächlich folgt.
 *
 * Alle drei Angaben zusammen, weil die Oberfläche sie zusammen braucht: Sie zeigt die eigene Wahl,
 * daneben den geerbten Wert ("Veranstaltung: an") und stellt sicher, dass niemand raten muss, was
 * am Ende gilt.
 */
data class RoundProgressionConfigDto(
    /** Die eigene Wahl. null = der Veranstaltung folgen. */
    val autoCreateFollowingRounds: Boolean?,
    /** Was die Veranstaltung vorgibt — nur zur Anzeige. */
    val eventAutoCreateFollowingRounds: Boolean,
    /** Was daraus folgt. Vom Backend gerechnet, damit die Regel nicht im Frontend zweitgeschrieben wird. */
    val effective: Boolean,
)

data class RoundProgressionConfigRequest(
    val autoCreateFollowingRounds: Boolean?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example get() = RoundProgressionConfigRequest(autoCreateFollowingRounds = null)
    }
}
