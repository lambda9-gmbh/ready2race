package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.notEmpty
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import java.util.*

data class WebDAVExportEventRequest(
    val eventId: UUID,
    val selectedExports: List<WebDAVExportType>,
    val selectedCompetitions: List<UUID>,
) : Validatable {
    override fun validate(): ValidationResult =
        this::selectedExports validate allOf(
            notEmpty,
            noDuplicates,
            /*oneOf( // todo: doesnt work
                isValue(WebDAVExportType.REGISTRATION_RESULTS),
                isValue(WebDAVExportType.INVOICES),
                isValue(WebDAVExportType.DOCUMENTS),
                isValue(WebDAVExportType.RESULTS),
                isValue(WebDAVExportType.START_LISTS),
                isValue(WebDAVExportType.DB_EVENT),
            )*/
        )


    companion object {
        val example
            get() = WebDAVExportEventRequest(
                eventId = UUID.randomUUID(),
                selectedExports = WebDAVExportType.entries,
                selectedCompetitions = listOf(UUID.randomUUID()),
            )
    }
}
