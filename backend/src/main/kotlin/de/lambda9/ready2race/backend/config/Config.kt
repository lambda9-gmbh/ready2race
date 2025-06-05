package de.lambda9.ready2race.backend.config

import de.lambda9.ready2race.backend.applyNotNull
import de.lambda9.ready2race.backend.kio.unwrap
import de.lambda9.ready2race.backend.parsing.Parser
import de.lambda9.ready2race.backend.parsing.Parser.Companion.boolean
import de.lambda9.ready2race.backend.parsing.Parser.Companion.enum
import de.lambda9.ready2race.backend.parsing.Parser.Companion.int
import io.github.cdimascio.dotenv.Dotenv
import org.simplejavamail.api.mailer.Mailer
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.mailer.MailerBuilder
import kotlin.enums.enumEntries
import kotlin.reflect.KClass

data class Config(
    val mode: Mode,
    val http: Http,
    val database: Database,
    val smtp: Smtp?,
    val security: Security,
    val admin: Admin,
) {

    enum class Mode {
        /**
         * A mode for local development.
         */
        DEV,

        /**
         * A mode for a test server deployment
         */
        STAGING,

        /**
         * A mode for a production server deployment
         */
        PROD,

        /**
         * A mode, when tests run.
         */
        TEST,
    }

    data class Http(
        val host: String,
        val port: Int,
    )

    data class Database(
        val url: String,
        val user: String,
        val password: String,
        val logQueries: Boolean,
    )

    data class Smtp(
        val host: String,
        val port: Int,
        val user: String,
        val password: String,
        val strategy: TransportStrategy,
        val from: From,
        val replyTo: String?,
        val localhost: String?,
    ) {
        data class From(
            val name: String?,
            val address: String,
        )

        fun createMailer(): Mailer = MailerBuilder
            .withSMTPServerHost(host)
            .withSMTPServerPort(port)
            .withSMTPServerUsername(user)
            .withSMTPServerPassword(password)
            .withTransportStrategy(strategy)
            .applyNotNull(localhost) {
                withProperties(mapOf("mail.smtp.localhost" to it))
            }
            .buildMailer()
    }

    data class Security(
        val pepper: String
    )

    data class Admin(
        val email: String,
        val password: String,
    )

    companion object {

        private inline fun <reified T: Any> Dotenv.optional(key: String, parser: Parser<T>): T? =
            get(key)?.let { value ->
                parser(value) { task ->
                    task.mapError { ParseEnvException("Value '$value' for key '$key' could not be parsed as '${T::class}'.") }.unwrap()
                }
            }

        private fun Dotenv.optional(key: String): String? =
            optional(key) { it }

        private inline fun <reified T: Any> Dotenv.required(key: String, parser: Parser<T>): T =
            optional(key, parser) ?: throw ParseEnvException("Missing required environment variable '$key'")

        private fun Dotenv.required(key: String): String =
            required(key) { it }

        fun Dotenv.parseConfig(): Config = Config(
            mode = required("MODE", enum<Mode>()),
            http = Http(
                host = required("HTTP_HOST"),
                port = required("HTTP_PORT", int),
            ),
            database = Database(
                url = required("DATABASE_URL"),
                user = required("DATABASE_USER"),
                password = required("DATABASE_PASSWORD"),
                logQueries = optional("DATABASE_LOG_QUERIES", boolean) ?: false
            ),
            smtp = Smtp(
                host = required("SMTP_HOST"),
                port = required("SMTP_PORT", int),
                user = required("SMTP_USER"),
                password = required("SMTP_PASSWORD"),
                strategy = required("SMTP_STRATEGY", enum<TransportStrategy>()),
                from = Smtp.From(
                    name = required("SMTP_FROM_NAME"),
                    address = required("SMTP_FROM_ADDRESS")
                ),
                replyTo = required("SMTP_REPLY"),
                localhost = required("SMTP_LOCALHOST")
            ),
            security = Security(
                pepper = required("SECURITY_PEPPER"),
            ),
            admin = Admin(
                email = required("ADMIN_EMAIL"),
                password = required("ADMIN_PASSWORD")
            )
        )
    }

}