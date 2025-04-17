package de.lambda9.ready2race.backend.app.documentTemplate.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class DocumentTemplateRequest(
    val pagePaddingTop: Double?,
    val pagePaddingLeft: Double?,
    val pagePaddingRight: Double?,
    val pagePaddingBottom: Double?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example get() = DocumentTemplateRequest(
            pagePaddingTop = 25.0,
            pagePaddingLeft = 25.0,
            pagePaddingRight = 20.0,
            pagePaddingBottom = 25.0,
        )
    }
}
