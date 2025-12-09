package de.lambda9.ready2race.backend.config

import de.lambda9.ready2race.backend.applyNotNull
import de.lambda9.ready2race.backend.parsing.Parser
import de.lambda9.ready2race.backend.parsing.Parser.Companion.boolean
import de.lambda9.ready2race.backend.parsing.Parser.Companion.enum
import de.lambda9.ready2race.backend.parsing.Parser.Companion.int
import de.lambda9.tailwind.core.Cause
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrElse
import de.lambda9.tailwind.core.extensions.exit.getOrThrow
import de.lambda9.tailwind.core.extensions.kio.andThenNotNull
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.recover
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
    val webDAV: WebDAV?
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
            .withProperties(mapOf("mail.mime.allowutf8" to "true"))
            .buildMailer()
    }

    data class Security(
        val pepper: String
    )

    data class Admin(
        val email: String,
        val password: String,
    )

    data class WebDAV(
        val urlScheme: String,
        val host: String,
        val path: String?,
        val folderPath: String?,
        val authUser: String,
        val authPassword: String,
    )

    companion object {

        private inline fun <reified T : Any> Dotenv.optional(key: String, parser: Parser<T>): IO<ParseEnvError, T?> =
            KIO.ok(get(key)).andThenNotNull { value ->
                parser(value) { task ->
                    task.mapError { ParseEnvError.UnparsableValue(key, value, T::class) }
                }
            }

        private fun Dotenv.optional(key: String): IO<ParseEnvError, String?> =
            optional(key) { it }

        private inline fun <reified T : Any> Dotenv.required(key: String, parser: Parser<T>): IO<ParseEnvError, T> =
            optional(key, parser).onNullFail { ParseEnvError.MissingKey(key) }

        private fun Dotenv.required(key: String): IO<ParseEnvError, String> =
            required(key) { it }

        fun Dotenv.parseConfig(): Config = KIO.comprehension {

            val mode = !required("MODE", enum<Mode>())

            val http = run {
                val host = !required("HTTP_HOST")
                val port = !required("HTTP_PORT", int)

                Http(host = host, port = port)
            }

            val database = run {
                val url = !required("DATABASE_URL")
                val user = !required("DATABASE_USER")
                val password = !required("DATABASE_PASSWORD")
                val logQueries = !optional("DATABASE_LOG_QUERIES", boolean) ?: false

                Database(url = url, user = user, password = password, logQueries = logQueries)
            }

            val smtp = run {
                val host = !required("SMTP_HOST")
                val port = !required("SMTP_PORT", int)
                val user = !required("SMTP_USER")
                val password = !required("SMTP_PASSWORD")
                val strategy = !required("SMTP_STRATEGY", enum<TransportStrategy>())
                val name = !optional("SMTP_FROM_NAME")
                val address = !required("SMTP_FROM_ADDRESS")
                val replyTo = !optional("SMTP_REPLY")
                val localhost = !optional("SMTP_LOCALHOST")

                val from = Smtp.From(name = name, address = address)

                Smtp(
                    host = host,
                    port = port,
                    user = user,
                    password = password,
                    strategy = strategy,
                    from = from,
                    replyTo = replyTo,
                    localhost = localhost
                )
            }

            val security = run {
                val pepper = !required("SECURITY_PEPPER")

                Security(pepper = pepper)
            }

            val admin = run {
                val email = !required("ADMIN_EMAIL")
                val password = !required("ADMIN_PASSWORD")

                Admin(email = email, password = password)
            }

            val webDAV = run {
                val urlScheme = !optional("WEBDAV_URL_SCHEME")
                val host = !optional("WEBDAV_HOST")
                val path = !optional("WEBDAV_PATH")
                val folderPath = !optional("WEBDAV_FOLDER_PATH")
                val authUser = !optional("WEBDAV_AUTH_USER")
                val authPassword = !optional("WEBDAV_AUTH_PASSWORD")

                if (urlScheme != null && host != null && authUser != null && authPassword != null) {
                    WebDAV(
                        urlScheme = urlScheme,
                        host = host,
                        path = path,
                        folderPath = folderPath,
                        authUser = authUser,
                        authPassword = authPassword
                    )
                } else if (urlScheme == null && host == null && authUser == null && authPassword == null && path == null && folderPath == null) {
                    null
                } else {
                    !KIO.fail(
                        listOf(
                            "WEBDAV_URL_SCHEME" to urlScheme,
                            "WEBDAV_HOST" to host,
                            "WEBDAV_PATH" to path,
                            "WEBDAV_FOLDER_PATH" to folderPath,
                            "WEBDAV_AUTH_USER" to authUser,
                            "WEBDAV_AUTH_PASSWORD" to authPassword
                        ).partition { it.second != null }
                            .let { (given, missing) ->
                                ParseEnvError.IncompleteBundle(
                                    given = given.map { it.first },
                                    missing = missing.map { it.first }
                                )
                            }
                    )
                }
            }

            val config = Config(
                mode = mode,
                http = http,
                database = database,
                smtp = smtp,
                security = security,
                admin = admin,
                webDAV = webDAV
            )

            KIO.ok(config)
        }
            .unwrap()
    }

}