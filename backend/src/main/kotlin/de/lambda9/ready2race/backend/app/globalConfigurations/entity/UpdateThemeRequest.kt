package de.lambda9.ready2race.backend.app.globalConfigurations.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class UpdateThemeRequest(
    val primary: PrimaryColorsRequest,
    val textColor: TextColorsRequest,
    val actionColors: ActionColorsRequest,
    val backgroundColor: String,
    val enableCustomFont: Boolean,
) : Validatable {
    override fun validate(): ValidationResult {
        val hexColorPattern = Regex("^#[0-9a-fA-F]{6}$")

        return when {
            !primary.main.matches(hexColorPattern) ->
                ValidationResult.Invalid.Message { "primary.main must be a valid hex color (e.g., #4d9f85)" }

            !primary.light.matches(hexColorPattern) ->
                ValidationResult.Invalid.Message { "primary.light must be a valid hex color (e.g., #ecfaf7)" }

            !textColor.primary.matches(hexColorPattern) ->
                ValidationResult.Invalid.Message { "textColor.primary must be a valid hex color (e.g., #1c71d8)" }

            !textColor.secondary.matches(hexColorPattern) ->
                ValidationResult.Invalid.Message { "textColor.secondary must be a valid hex color (e.g., #666666)" }

            !actionColors.success.matches(hexColorPattern) ->
                ValidationResult.Invalid.Message { "actionColors.success must be a valid hex color (e.g., #cbe694)" }

            !actionColors.warning.matches(hexColorPattern) ->
                ValidationResult.Invalid.Message { "actionColors.warning must be a valid hex color (e.g., #f5d9b0)" }

            !actionColors.error.matches(hexColorPattern) ->
                ValidationResult.Invalid.Message { "actionColors.error must be a valid hex color (e.g., #da4d4d)" }

            !actionColors.info.matches(hexColorPattern) ->
                ValidationResult.Invalid.Message { "actionColors.info must be a valid hex color (e.g., #6fb0d4)" }

            !backgroundColor.matches(hexColorPattern) ->
                ValidationResult.Invalid.Message { "backgroundColor must be a valid hex color (e.g., #ffffff)" }

            else -> ValidationResult.Valid
        }
    }

    data class PrimaryColorsRequest(
        val main: String,
        val light: String
    )

    data class TextColorsRequest(
        val primary: String,
        val secondary: String
    )

    data class ActionColorsRequest(
        val success: String,
        val warning: String,
        val error: String,
        val info: String
    )

    companion object {
        val example = UpdateThemeRequest(
            primary = PrimaryColorsRequest(
                main = "#4d9f85",
                light = "#ecfaf7"
            ),
            textColor = TextColorsRequest(
                primary = "#1c71d8",
                secondary = "#666666"
            ),
            actionColors = ActionColorsRequest(
                success = "#cbe694",
                warning = "#f5d9b0",
                error = "#da4d4d",
                info = "#6fb0d4"
            ),
            backgroundColor = "#ffffff",
            enableCustomFont = false
        )
    }
}
