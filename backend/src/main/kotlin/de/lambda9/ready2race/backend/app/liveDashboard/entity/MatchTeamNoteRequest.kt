package de.lambda9.ready2race.backend.app.liveDashboard.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

/**
 * Eine neue Notiz zu einem Boot. Mehr als den Text gibt es nicht anzugeben - Autorin und
 * Zeitpunkt setzt der Server, und geändert wird ein Eintrag nie (append-only).
 *
 * [note] ist Pflicht - hier durch den Validator, in der Datenbank noch einmal durch
 * `check (btrim(note) <> '')`: eine leere Notiz hätte den anderen Schiedsrichtern nichts zu sagen.
 */
data class MatchTeamNoteRequest(
    val note: String,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::note validate notBlank,
    )

    companion object {
        val example
            get() = MatchTeamNoteRequest(
                note = "Boje berührt",
            )
    }
}
