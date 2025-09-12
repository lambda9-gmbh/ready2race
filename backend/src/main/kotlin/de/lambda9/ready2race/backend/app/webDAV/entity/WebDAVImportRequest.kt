package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.notEmpty
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class WebDAVImportRequest(
    val folderName: String,
    val selectedData: List<WebDAVExportType>,
): Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::folderName validate notBlank,
            this::selectedData validate notEmpty
        )

    companion object {
        val example get() = WebDAVImportRequest(
            folderName = "2025-EventName",
            selectedData = listOf(WebDAVExportType.DB_USERS, WebDAVExportType.DB_CLUBS),
        )
    }
}
