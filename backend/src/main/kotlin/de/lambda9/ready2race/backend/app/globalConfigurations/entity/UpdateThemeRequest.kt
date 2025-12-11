package de.lambda9.ready2race.backend.app.globalConfigurations.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class UpdateThemeRequest(
    val primaryColor: String,
    val textColor: String,
    val backgroundColor: String,
    val enableCustomFont: Boolean,
) : Validatable {
    override fun validate(): ValidationResult {
        val hexColorPattern = Regex("^#[0-9a-fA-F]{6}$")

        return when {
            !primaryColor.matches(hexColorPattern) ->
                ValidationResult.Invalid.Message { "primaryColor must be a valid hex color (e.g., #4d9f85)" }
            !textColor.matches(hexColorPattern) ->
                ValidationResult.Invalid.Message { "textColor must be a valid hex color (e.g., #1d1d1d)" }
            !backgroundColor.matches(hexColorPattern) ->
                ValidationResult.Invalid.Message { "backgroundColor must be a valid hex color (e.g., #ffffff)" }
            else -> ValidationResult.Valid
        }
    }

    companion object {
        val example = UpdateThemeRequest(
            primaryColor = "#4d9f85",
            textColor = "#1d1d1d",
            backgroundColor = "#ffffff",
            enableCustomFont = false
        )
    }
}
