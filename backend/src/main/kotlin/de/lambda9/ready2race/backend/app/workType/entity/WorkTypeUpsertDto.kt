package de.lambda9.ready2race.backend.app.workType.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class WorkTypeUpsertDto(
    var name: String,
    var description: String?,
    var color: String?,
    var minUser: Int,
    var maxUser: Int?,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.Valid

    companion object {
        val example
            get() = WorkTypeUpsertDto(
                name = "Name",
                description = "...",
                color = "#FFFFFF",
                minUser = 1,
                maxUser = 5
            )
    }
}