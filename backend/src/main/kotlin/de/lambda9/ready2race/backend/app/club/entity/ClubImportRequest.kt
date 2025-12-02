package de.lambda9.ready2race.backend.app.club.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class ClubImportRequest(
    val separator: Char,
    val colName: String,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {

        val example get() = ClubImportRequest(
            separator = ',',
            colName = "Verein",
        )
    }
}
