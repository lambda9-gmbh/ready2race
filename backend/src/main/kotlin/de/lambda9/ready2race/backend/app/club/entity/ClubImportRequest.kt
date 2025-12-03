package de.lambda9.ready2race.backend.app.club.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class ClubImportRequest(
    val separator: Char,
    val charset: String,
    val colName: String,
    val noHeader: Boolean,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {

        val example get() = ClubImportRequest(
            separator = ',',
            charset = "UTF-8",
            colName = "Verein",
            noHeader = false,
        )
    }
}
