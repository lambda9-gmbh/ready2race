package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.notEmpty
import de.lambda9.ready2race.backend.validation.validators.StringValidators.maxLength
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isValue
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.oneOf

data class WebDAVExportRequest(
    val name: String,
    val events: List<WebDAVExportEventRequest>,
    val selectedDatabaseExports: List<WebDAVExportType>,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::name validate notBlank,
            this::name validate maxLength(255),
            this::events validate collection,
            this::selectedDatabaseExports validate allOf(
                notEmpty,
                noDuplicates,
                collection(
                    oneOf(
                        isValue(WebDAVExportType.DB_USERS),
                        isValue(WebDAVExportType.DB_PARTICIPANTS),
                        isValue(WebDAVExportType.DB_BANK_ACCOUNTS),
                        isValue(WebDAVExportType.DB_CONTACT_INFORMATION),
                        isValue(WebDAVExportType.DB_EMAIL_INDIVIDUAL_TEMPLATES),
                        isValue(WebDAVExportType.DB_EVENT_DOCUMENT_TYPES),
                        isValue(WebDAVExportType.DB_MATCH_RESULT_IMPORT_CONFIGS),
                        isValue(WebDAVExportType.DB_STARTLIST_EXPORT_CONFIGS),
                        isValue(WebDAVExportType.DB_WORK_TYPES),
                        isValue(WebDAVExportType.DB_PARTICIPANT_REQUIREMENTS),
                        isValue(WebDAVExportType.DB_RATING_CATEGORIES),
                        isValue(WebDAVExportType.DB_COMPETITION_CATEGORIES),
                        isValue(WebDAVExportType.DB_FEES),
                        isValue(WebDAVExportType.DB_NAMED_PARTICIPANTS),
                        isValue(WebDAVExportType.DB_COMPETITION_SETUP_TEMPLATES),
                        isValue(WebDAVExportType.DB_COMPETITION_TEMPLATES),
                    )
                )
            )
        )

    companion object {
        val example
            get() = WebDAVExportRequest(
                name = "2025-EventName",
                events = listOf(WebDAVExportEventRequest.example),
                selectedDatabaseExports = WebDAVExportType.entries,
            )
    }
}
