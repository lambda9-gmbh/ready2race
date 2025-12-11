package de.lambda9.ready2race.backend.app.globalConfigurations.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.globalConfigurations.control.ThemeRepo
import de.lambda9.ready2race.backend.app.globalConfigurations.entity.CustomFontDto
import de.lambda9.ready2race.backend.app.globalConfigurations.entity.ThemeConfigDto
import de.lambda9.ready2race.backend.app.globalConfigurations.entity.ThemeError
import de.lambda9.ready2race.backend.app.globalConfigurations.entity.UpdateThemeRequest
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO

object ThemeService {

    fun getTheme(env: JEnv): App<ThemeError, ApiResponse.Dto<ThemeConfigDto>> = KIO.comprehension {
        val theme = !ThemeRepo.getTheme(env.env.config.staticFilesPath)
        KIO.ok(ApiResponse.Dto(theme))
    }

    fun updateTheme(
        env: JEnv,
        request: UpdateThemeRequest,
        fontFile: Pair<String, ByteArray>?
    ): App<ThemeError, ApiResponse.Dto<ThemeConfigDto>> = KIO.comprehension {
        val currentTheme = !ThemeRepo.getTheme(env.env.config.staticFilesPath)
        val staticFilesPath = env.env.config.staticFilesPath

        // Handle font file upload
        val newCustomFont = when {
            fontFile != null && request.enableCustomFont -> {
                // Delete old font if it exists
                currentTheme.customFont.filename?.let { oldFilename ->
                    !ThemeRepo.deleteFontFile(staticFilesPath, oldFilename)
                }

                // Save new font
                val savedFilename = !ThemeRepo.saveFontFile(staticFilesPath, fontFile.first, fontFile.second)
                CustomFontDto(enabled = true, filename = savedFilename)
            }
            request.enableCustomFont && currentTheme.customFont.filename != null -> {
                // Keep existing font
                currentTheme.customFont
            }
            !request.enableCustomFont -> {
                // Delete font if disabling
                currentTheme.customFont.filename?.let { oldFilename ->
                    !ThemeRepo.deleteFontFile(staticFilesPath, oldFilename)
                }
                CustomFontDto.default
            }
            else -> CustomFontDto.default
        }

        // Update theme
        val updatedTheme = ThemeConfigDto(
            version = "1.0",
            primaryColor = request.primaryColor,
            textColor = request.textColor,
            backgroundColor = request.backgroundColor,
            customFont = newCustomFont
        )

        !ThemeRepo.updateTheme(staticFilesPath, updatedTheme)

        KIO.ok(ApiResponse.Dto(updatedTheme))
    }

    fun resetTheme(env: JEnv): App<ThemeError, ApiResponse.Dto<ThemeConfigDto>> = KIO.comprehension {
        val defaultTheme = !ThemeRepo.resetToDefault(env.env.config.staticFilesPath)
        KIO.ok(ApiResponse.Dto(defaultTheme))
    }
}
