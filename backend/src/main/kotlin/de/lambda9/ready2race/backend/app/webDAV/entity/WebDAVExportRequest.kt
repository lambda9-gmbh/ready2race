package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.fileNamePreDot
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.StringValidators.pattern
import java.util.UUID

data class WebDAVExportRequest(
    val name: String,
    val events: List<UUID>
): Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::name validate notBlank,
            this::name validate pattern(fileNamePreDot),
        )

    companion object {
        val example get() = WebDAVExportRequest(
            name = "2025-EventName",
            events = listOf(UUID.randomUUID()),
        )
    }
}
