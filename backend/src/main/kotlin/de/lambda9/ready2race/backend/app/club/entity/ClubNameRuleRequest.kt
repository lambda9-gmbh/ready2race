package de.lambda9.ready2race.backend.app.club.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID

/**
 * Anlegen und Ändern einer Regel.
 *
 * [term] und [replacement] hängen von der Art ab; die Prüfung steht deshalb im Service und nicht
 * hier: was gültig ist, weiß erst, wer die Art kennt.
 */
data class ClubNameRuleRequest(
    val kind: ClubNameRuleKind,
    val term: String?,
    val replacement: String?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = ClubNameRuleRequest(
                kind = ClubNameRuleKind.ABBREVIATION,
                term = "Ruderverein",
                replacement = "RV",
            )
    }
}

/**
 * Die neue Reihenfolge, vollständig. Eine Umsortierung ist keine Änderung an einer einzelnen Zeile:
 * würde der Aufrufer zwei Zeilen nacheinander schreiben, gäbe es dazwischen eine Reihenfolge, die
 * es nie geben sollte.
 */
data class ClubNameRuleOrderRequest(
    val ruleIds: List<UUID>,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = ClubNameRuleOrderRequest(ruleIds = listOf(UUID.randomUUID()))
    }
}
