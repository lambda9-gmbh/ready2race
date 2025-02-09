package de.lambda9.ready2race.backend

import de.lambda9.ready2race.backend.Config.Companion.parseConfig
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.Env
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.appuser.boundary.AppUserService
import de.lambda9.ready2race.backend.app.auth.boundary.AuthService
import de.lambda9.ready2race.backend.app.auth.control.PrivilegeRepo
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.role.control.RoleRepo
import de.lambda9.ready2race.backend.app.appuser.control.AppUserHasRoleRepo
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.email.entity.EmailError
import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.database.ADMIN_ROLE
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.PrivilegeRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RoleRecord
import de.lambda9.ready2race.backend.plugins.*
import de.lambda9.ready2race.backend.schedule.JobQueueState
import de.lambda9.ready2race.backend.schedule.Scheduler
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrThrow
import de.lambda9.tailwind.core.extensions.kio.catchError
import de.lambda9.tailwind.core.extensions.kio.orDie
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

    initializeApplication(env)
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

private fun initializeApplication(env: JEnv) {

    // instantiate now, so template files get read and checked
    EmailService

    KIO.comprehension {

        // Add admin

        val adminUserExisting = !AppUserRepo.exists(SYSTEM_USER)
        val adminRoleExisting = !RoleRepo.exists(ADMIN_ROLE)

        val admin = env.env.config.admin

        val hashedPw = !PasswordUtilities.hash(admin.password)

        val now = LocalDateTime.now()

        if (!adminUserExisting) {

            !AppUserRepo.create(
                AppUserRecord(
                    id = SYSTEM_USER,
                    email = admin.email,
                    firstname = "System",
                    lastname = "User",
                    password = hashedPw,
                    language = EmailLanguage.DE.name,
                    createdAt = now,
                    createdBy = SYSTEM_USER,
                    updatedAt = now,
                    updatedBy = SYSTEM_USER,
                )
            )
        } else {

            !AppUserRepo.update(SYSTEM_USER) {
                email = admin.email
                password = hashedPw
                updatedAt = now
                updatedBy = SYSTEM_USER
            }
        }

        if (!adminRoleExisting) {
            !RoleRepo.create(
                RoleRecord(
                    id = ADMIN_ROLE,
                    name = "Admin",
                    description = "Global admin role",
                    static = true,
                    assignable = false,
                    createdAt = now,
                    createdBy = SYSTEM_USER,
                    updatedAt = now,
                    updatedBy = SYSTEM_USER,
                )
            )
        }

        if (!adminUserExisting || !adminRoleExisting) {
            !AppUserHasRoleRepo.create(SYSTEM_USER, listOf(ADMIN_ROLE))
        }

        // Add missing privileges

        val privilegeRecords = !PrivilegeRepo.all()
        val records = Privilege.entries
            .filter { p ->
                privilegeRecords.none {
                    it.action == p.action.name
                        && it.resource == p.resource.name
                        && it.scope == p.scope.name
                }
            }.map {
                PrivilegeRecord(
                    id = UUID.randomUUID(),
                    action = it.action.name,
                    resource = it.resource.name,
                    scope = it.scope.name,
                )
            }
        !PrivilegeRepo.create(records)

        App.unit
    }
        .transact()
        .unsafeRunSync(env)
        .getOrThrow()
}

private fun CoroutineScope.scheduleJobs(env: JEnv) = with(Scheduler(env)) {
    launch(Dispatchers.IO) {
        supervisorScope {
            logger.info { "Scheduling jobs ..." }

            scheduleDynamic(10.seconds) {
                EmailService.sendNext()
                    .map { JobQueueState.PROCESSED }
                    .catchError { error ->
                        KIO.ok(
                            when (error) {
                                EmailError.NoEmailsToSend -> JobQueueState.EMPTY
                                is EmailError.SendingFailed -> {
                                    logger.warn(error.cause) { "Error sending email ${error.emailId}" }
                                    JobQueueState.PROCESSED
                                }
                            }
                        )
                    }
            }

            /*scheduleFixed(1.hours) {
                EmailService.deleteSent().map {
                    logger.info { "${"sent email".count(it)} deleted" }
                }
            }*/

            scheduleFixed(5.minutes) {
                AuthService.deleteExpiredTokens().map {
                    logger.info { "${"expired session".count(it)} deleted" }
                }
            }

            scheduleFixed(1.hours) {
                AppUserService.deleteExpiredRegistrations().map {
                    logger.info { "${"expired registration".count(it)} deleted" }
                }
            }

            scheduleFixed(1.hours) {
                AppUserService.deleteExpiredInvitations().map {
                    logger.info { "${"expired invitations".count(it)} deleted" }
                }
            }

            logger.info { "Scheduling done."}
        }
    }
}