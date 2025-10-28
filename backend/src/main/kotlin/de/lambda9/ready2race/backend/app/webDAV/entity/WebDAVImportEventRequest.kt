package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class WebDAVImportEventRequest(
    val eventFolderName: String,
    val competitionFolderNames: List<String>
) : Validatable {
    override fun validate(): ValidationResult = this::eventFolderName validate notBlank

    companion object {
        val example
            get() = WebDAVImportEventRequest(
                eventFolderName = "Event",
                competitionFolderNames = listOf("Competition-1")
            )
    }
}
