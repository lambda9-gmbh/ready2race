package de.lambda9.ready2race.backend

import de.lambda9.ready2race.backend.Config.Companion.parseConfig
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.Env
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.appuser.boundary.AppUserService
import de.lambda9.ready2race.backend.app.appuser.control.AppUserHasRoleRepo
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.auth.boundary.AuthService
import de.lambda9.ready2race.backend.app.auth.control.PrivilegeRepo
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.role.control.RoleRepo
import de.lambda9.ready2race.backend.app.captcha.boundary.CaptchaService
import de.lambda9.ready2race.backend.app.email.entity.EmailError
import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.app.role.control.RoleHasPrivilegeRepo
import de.lambda9.ready2race.backend.database.ADMIN_ROLE
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.USER_ROLE
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.database.initializeDatabase
import de.lambda9.ready2race.backend.plugins.*
import de.lambda9.ready2race.backend.schedule.DynamicIntervalJobState
import de.lambda9.ready2race.backend.schedule.FixedIntervalJobState
import de.lambda9.ready2race.backend.schedule.Scheduler
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrThrow
import de.lambda9.tailwind.core.extensions.kio.recoverDefault
import de.lambda9.tailwind.jooq.transact
import io.github.cdimascio.dotenv.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import org.flywaydb.core.Flyway
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>): Unit = runBlocking {
    val config = dotenv {
        filename = args.getOrNull(0) ?: ".env"
    }.parseConfig()
    val (env, ds) = Env.create(config)

    Flyway(
        Flyway.configure()
            .dataSource(ds)
            .schemas("ready2race")
    ).migrate()

    initializeDatabase(env)
    scheduleJobs(env)

    embeddedServer(Netty, port = config.http.port, host = config.http.host, module = { module(env) })
        .start(wait = true)
}

fun Application.module(env: JEnv) {
    configureKIO(env)
    configureHTTP(env.env.config.mode)
    configureSerialization()
    configureSessions()
    configureRequests()
    configureResponses()
    configureRouting()
}

private fun CoroutineScope.scheduleJobs(env: JEnv) = with(Scheduler(env)) {
    launch(Dispatchers.IO) {
        supervisorScope {
            logger.info { "Scheduling jobs ..." }

            scheduleDynamic("Send next email", 10.seconds) {
                EmailService.sendNext()
                    .map { DynamicIntervalJobState.Processed }
                    .recoverDefault { error ->
                        when (error) {
                            EmailError.SmtpConfigMissing -> DynamicIntervalJobState.Fatal("Smtp config missing")
                            EmailError.NoEmailsToSend -> DynamicIntervalJobState.Empty
                            is EmailError.SendingFailed -> {
                                logger.warn(error.cause) { "Error sending email ${error.emailId}" }
                                DynamicIntervalJobState.Processed
                            }
                        }
                    }
            }

            /*scheduleFixed("Delete sent emails", 1.hours) {
                EmailService.deleteSent().map {
                    logger.info { "${"sent email".count(it)} deleted" }
                }
            }*/

            scheduleFixed("Delete expired session tokens", 5.minutes) {
                AuthService.deleteExpiredTokens().map {
                    logger.info { "${"expired session".count(it)} deleted" }
                }
            }

            scheduleFixed("Delete expired user registrations", 1.hours) {
                AppUserService.deleteExpiredRegistrations().map {
                    logger.info { "${"expired registration".count(it)} deleted" }
                }
            }

            scheduleFixed("Delete expired user invitations", 1.hours) {
                AppUserService.deleteExpiredInvitations().map {
                    logger.info { "${"expired invitations".count(it)} deleted" }
                }
            }

            scheduleFixed("Delete expired password resets", 1.hours) {
                AppUserService.deleteExpiredPasswordResets().map {
                    logger.info { "${"expired password resets".count(it)} deleted" }
                }
            }

            scheduleFixed("Delete expired captchas", 5.minutes){
                CaptchaService.deleteExpired().map{
                    logger.info { "${"expired captchas".count(it)} deleted" }
                }
            }

            logger.info { "Scheduling done."}
        }
    }
}