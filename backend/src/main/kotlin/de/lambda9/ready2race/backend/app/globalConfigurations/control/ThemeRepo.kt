package de.lambda9.ready2race.backend.app.globalConfigurations.control

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.globalConfigurations.entity.CustomFontDto
import de.lambda9.ready2race.backend.app.globalConfigurations.entity.ThemeConfigDto
import de.lambda9.ready2race.backend.app.globalConfigurations.entity.ThemeError
import de.lambda9.tailwind.core.KIO
import java.io.File

object ThemeRepo {
    private const val MAX_FONT_SIZE_BYTES = 5 * 1024 * 1024 // 5MB
    private val ALLOWED_FONT_EXTENSIONS = setOf("woff", "woff2")

    private val objectMapper = ObjectMapper().writerWithDefaultPrettyPrinter()
    private val objectReader = ObjectMapper()

    fun getTheme(staticFilesPath: String): KIO<Any, ThemeError, ThemeConfigDto> = KIO.effect {
        val themeFile = File(staticFilesPath, "theme.json")

        if (!themeFile.exists()) {
            return@effect ThemeConfigDto.default
        }

        try {
            objectReader.readValue<ThemeConfigDto>(themeFile)
        } catch (e: Exception) {
            !KIO.fail(ThemeError.ThemeFileMalformed)
        }
    }

    fun updateTheme(staticFilesPath: String, theme: ThemeConfigDto): KIO<Any, ThemeError, Unit> = KIO.effect {
        val themeFile = File(staticFilesPath, "theme.json")
        themeFile.parentFile?.mkdirs()
        objectMapper.writeValue(themeFile, theme)
    }

    fun saveFontFile(staticFilesPath: String, filename: String, content: ByteArray): KIO<Any, ThemeError, String> = KIO.effect {
        val extension = filename.substringAfterLast('.', "").lowercase()

        if (extension !in ALLOWED_FONT_EXTENSIONS) {
            !KIO.fail(ThemeError.FontFileInvalid)
        }

        if (content.size > MAX_FONT_SIZE_BYTES) {
            !KIO.fail(ThemeError.FontFileTooLarge)
        }

        val fontsDir = File(staticFilesPath, "fonts")
        fontsDir.mkdirs()

        val sanitizedFilename = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fontFile = File(fontsDir, sanitizedFilename)
        fontFile.writeBytes(content)

        sanitizedFilename
    }

    fun deleteFontFile(staticFilesPath: String, filename: String): KIO<Any, ThemeError, Unit> = KIO.effect {
        val fontFile = File(File(staticFilesPath, "fonts"), filename)
        if (fontFile.exists()) {
            fontFile.delete()
        }
    }

    fun resetToDefault(staticFilesPath: String): KIO<Any, ThemeError, ThemeConfigDto> = KIO.effect {
        // Delete theme.json
        val themeFile = File(staticFilesPath, "theme.json")
        if (themeFile.exists()) {
            themeFile.delete()
        }

        // Delete all font files
        val fontsDir = File(staticFilesPath, "fonts")
        if (fontsDir.exists() && fontsDir.isDirectory) {
            fontsDir.listFiles()?.forEach { it.delete() }
        }

        // Write default theme
        !updateTheme(staticFilesPath, ThemeConfigDto.default)

        ThemeConfigDto.default
    }
}
