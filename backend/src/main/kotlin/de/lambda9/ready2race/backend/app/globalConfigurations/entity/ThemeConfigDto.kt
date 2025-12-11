package de.lambda9.ready2race.backend.app.globalConfigurations.entity

data class ThemeConfigDto(
    val primaryColor: String,
    val textColor: String,
    val backgroundColor: String,
    val customFont: CustomFontDto
) {
    companion object {
        val default = ThemeConfigDto(
            primaryColor = "#4d9f85",
            textColor = "#1d1d1d",
            backgroundColor = "#ffffff",
            customFont = CustomFontDto.default
        )
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
}


