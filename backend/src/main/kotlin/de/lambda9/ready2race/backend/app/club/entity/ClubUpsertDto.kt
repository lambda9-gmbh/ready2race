package de.lambda9.ready2race.backend.app.club.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class ClubUpsertDto(
    val name: String,
    /**
     * Die Kurzform aus dem Bearbeiten-Dialog - dieselbe Ablage wie auf der Pflegeseite, nämlich
     * `club_short_name` unter dem Schlüssel des Vereins*namens*. Bewusst keine Spalte an `club`:
     * den Verein eines Gastruderers gibt es nur als Freitext an der Person, und der bräuchte sonst
     * eine zweite Pflegestelle.
     *
     * Drei Zustände, deshalb `String?` und kein `String`:
     * - `null` - nicht angefasst, die Ablage bleibt, wie sie ist. Das ist auch der Fall für jeden
     *   Aufrufer, der die Kurzform gar nicht kennt (CSV-Import, ältere Clients).
     * - leer - Eintrag löschen, danach greift wieder die Automatik.
     * - Wert - Kurzform pflegen.
     *
     * Deshalb steht hier ausdrücklich **kein** [notBlank]: leer ist eine Aussage, kein Fehler.
     */
    val shortName: String? = null,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::name validate notBlank,
        )

    companion object {
        val example
            get() = ClubUpsertDto(
                name = "Name",
            )
    }
}