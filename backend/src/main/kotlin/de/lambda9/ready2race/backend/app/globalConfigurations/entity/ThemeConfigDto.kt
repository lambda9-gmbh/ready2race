package de.lambda9.ready2race.backend.app.globalConfigurations.entity

data class ThemeConfigDto(
    val primary: PrimaryColors,
    val textColor: TextColors,
    val actionColors: ActionColors,
    val backgroundColor: String,
    val customFont: CustomFontDto,
    val customLogo: CustomLogoDto
) {
    companion object {
        val default = ThemeConfigDto(
            primary = PrimaryColors.default,
            textColor = TextColors.default,
            actionColors = ActionColors.default,
            backgroundColor = "#ffffff",
            customFont = CustomFontDto.default,
            customLogo = CustomLogoDto.default
        )
    }

    data class PrimaryColors(
        val main: String,
        val light: String
    ) {
        companion object {
            val default = PrimaryColors(
                main = "#4d9f85",
                light = "#ecfaf7"
            )
        }
    }

    data class TextColors(
        val primary: String,
        val secondary: String
    ) {
        companion object {
            val default = TextColors(
                primary = "#1d1d1d",
                secondary = "#666666"
            )
        }
    }

    data class ActionColors(
        val success: String,
        val warning: String,
        val error: String,
        val info: String
    ) {
        companion object {
            val default = ActionColors(
                success = "#cbe694",
                warning = "#f5d9b0",
                error = "#da4d4d",
                info = "#6fb0d4"
            )
        }
    }

    data class CustomFontDto(
        val enabled: Boolean,
        val filename: String?
    ) {
        companion object {
            val default = CustomFontDto(
                enabled = false,
                filename = null
            )
        }
    }

    data class CustomLogoDto(
        val enabled: Boolean,
        val filename: String?
    ) {
        companion object {
            val default = CustomLogoDto(
                enabled = false,
                filename = null
            )
        }
    }
}


