package de.lambda9.ready2race.backend.config

import de.lambda9.ready2race.backend.config.Config.Companion.parseConfig
import io.github.cdimascio.dotenv.Dotenv
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigParseTest {
    @Test
    fun `empty optional Docker settings remain unset and origins are trimmed`() {
        val directory = Files.createTempDirectory("r2r-config-test")
        val file = directory.resolve("test.env")
        try {
            Files.writeString(file, """
                MODE=PROD
                HTTP_HOST=0.0.0.0
                HTTP_PORT=8080
                DATABASE_URL=jdbc:postgresql://db:5432/ready2race
                DATABASE_USER=ready2race
                DATABASE_PASSWORD=test-only
                SMTP_HOST=smtp.example.org
                SMTP_PORT=587
                SMTP_USER=test-only
                SMTP_PASSWORD=test-only
                SMTP_STRATEGY=SMTP_TLS
                SMTP_FROM_ADDRESS=test@example.org
                SMTP_REPLY=
                SMTP_LOCALHOST=
                ADMIN_EMAIL=admin@example.org
                ADMIN_PASSWORD=test-only
                SECURITY_PEPPER=test-only
                STATIC_FILES_PATH=/data/static
                WHITELIST_EMAIL_RECEIVER=
                WEBDAV_URL_SCHEME=
                WEBDAV_HOST=
                WEBDAV_PATH=
                WEBDAV_FOLDER_PATH=
                WEBDAV_AUTH_USER=
                WEBDAV_AUTH_PASSWORD=
                CORS_ALLOWED_ORIGINS="https://club.example.org, https://other.example.org:8443"
            """.trimIndent())
            val config = Dotenv.configure().directory(directory.toString()).filename("test.env").load().parseConfig()
            assertNull(config.webDAV)
            assertNull(config.smtp?.replyTo)
            assertNull(config.smtp?.localhost)
            assertNull(config.mailReceiverWhitelist)
            assertEquals(listOf("https://club.example.org", "https://other.example.org:8443"), config.corsAllowedOrigins)
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
