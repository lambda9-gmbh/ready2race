package de.lambda9.ready2race.backend

import io.github.cdimascio.dotenv.Dotenv
import org.simplejavamail.api.mailer.config.TransportStrategy

data class Config(
    val mode: Mode,
    val http: Http,
    val database: Database,
    val smtp: Smtp,
    val security: Security,
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
    )

    data class Smtp(
        val host: String,
        val port: Int,
        val user: String,
        val password: String,
        val strategy: TransportStrategy,
    )

    data class Security(
        val pepper: String
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
            ),
            smtp = Smtp(
                host = get("SMTP_HOST"),
                port = get("SMTP_PORT").toInt(),
                user = get("SMTP_USER"),
                password = get("SMTP_PASSWORD"),
                strategy = TransportStrategy.valueOf(get("SMTP_STRATEGY")),
            ),
            security = Security(
                pepper = get("SECURITY_PEPPER"),
            )
        )
    }

}