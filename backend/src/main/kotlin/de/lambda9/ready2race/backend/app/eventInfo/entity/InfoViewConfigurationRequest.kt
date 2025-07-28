package de.lambda9.ready2race.backend.app.eventInfo.entity

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.database.generated.enums.InfoViewType
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min
import de.lambda9.ready2race.backend.validation.validators.IntValidators.max

data class InfoViewConfigurationRequest(
    val viewType: InfoViewType,
    val displayDurationSeconds: Int,
    val dataLimit: Int,
    val filters: JsonNode?,
    val sortOrder: Int,
    val isActive: Boolean
) : Validatable {

    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::displayDurationSeconds validate min(1),
            this::dataLimit validate min(1),
            this::dataLimit validate max(100)
        )

    companion object {
        val example = InfoViewConfigurationRequest(
            viewType = InfoViewType.UPCOMING_COMPETITIONS,
            displayDurationSeconds = 10,
            dataLimit = 10,
            filters = null,
            sortOrder = 0,
            isActive = true
        )
    }
}