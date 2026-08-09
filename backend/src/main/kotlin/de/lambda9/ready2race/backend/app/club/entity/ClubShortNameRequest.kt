package de.lambda9.ready2race.backend.app.club.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

/**
 * [sampleName] fährt mit, weil der Schlüssel im Pfad nicht zurückgerechnet werden kann - er hat
 * Rechtsform, Jahreszahl und Trennzeichen verloren. Ohne eine Original-Schreibweise könnte die
 * Pflegeseite später nur den zusammengezogenen Schlüssel anzeigen.
 *
 * Eine leere Kurzform ist kein gültiger Rumpf: "Feld geleert" heißt "Eintrag löschen" und geht
 * über DELETE, nicht über ein PUT mit leerem Wert.
 */
data class ClubShortNameRequest(
    val shortName: String,
    val sampleName: String,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::shortName validate notBlank,
            this::sampleName validate notBlank,
        )

    companion object {
        val example
            get() = ClubShortNameRequest(
                shortName = "1. KRC",
                sampleName = "Erster Kieler Ruder-Club von 1862 e.V.",
            )
    }
}
