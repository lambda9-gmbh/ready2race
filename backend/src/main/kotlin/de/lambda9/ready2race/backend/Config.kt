package de.lambda9.ready2race.backend

import io.github.cdimascio.dotenv.Dotenv
import org.simplejavamail.api.mailer.Mailer
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.mailer.MailerBuilder

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

        fun Dotenv.parseConfig(): Config = Config(
            mode = Mode.valueOf(get("MODE")),
            http = Http(
                host = get("HTTP_HOST"),
                port = get("HTTP_PORT").toInt(),
            ),
            database = Database(
                url = get("DATABASE_URL"),
                user = get("DATABASE_USER"),
                password = get("DATABASE_PASSWORD"),
                logQueries = get("DATABASE_LOG_QUERIES")?.toBooleanStrict() ?: false
            ),
            smtp = Smtp(
                host = get("SMTP_HOST"),
                port = get("SMTP_PORT").toInt(),
                user = get("SMTP_USER"),
                password = get("SMTP_PASSWORD"),
                strategy = TransportStrategy.valueOf(get("SMTP_STRATEGY")),
                from = Smtp.From(
                    name = get("SMTP_FROM_NAME"),
                    address = get("SMTP_FROM_ADDRESS")
                ),
                replyTo = get("SMTP_REPLY"),
                localhost = get("SMTP_LOCALHOST")
            ),
            security = Security(
                pepper = get("SECURITY_PEPPER"),
            ),
            admin = Admin(
                email = get("ADMIN_EMAIL"),
                password = get("ADMIN_PASSWORD")
            )
        )
    }

}