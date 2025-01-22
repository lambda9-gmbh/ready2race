package de.lambda9.ready2race.backend

import de.lambda9.ready2race.backend.Config.Companion.parseConfig
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.Env
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.auth.control.PrivilegeRepo
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.role.control.RoleRepo
import de.lambda9.ready2race.backend.app.user.control.AppUserHasRoleRepo
import de.lambda9.ready2race.backend.app.user.control.AppUserRepo
import de.lambda9.ready2race.backend.database.ADMIN_ROLE
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RoleRecord
import de.lambda9.ready2race.backend.schedule.schedulingJobs
import de.lambda9.ready2race.backend.plugins.*
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrNullLogError
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.jooq.transact
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.flywaydb.core.Flyway
import java.time.LocalDateTime

fun main(args: Array<String>) {
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
    schedulingJobs(env)

    embeddedServer(Netty, port = config.http.port, host = config.http.host, module = { module(env) })
        .start(wait = true)
}

fun Application.module(env: JEnv) {
    configureAdministration()
    configureKIO(env)
    configureHTTP(env.env.config.mode)
    configureSerialization()
    configureRouting()
    configureValidation()
}

private fun initializeApplication(env: JEnv) {

    KIO.comprehension {

        // Add admin

        val adminUserExisting = !AppUserRepo.exists(SYSTEM_USER).orDie()
        val adminRoleExisting = !RoleRepo.exists(ADMIN_ROLE).orDie()

        val admin = env.env.config.admin

        val hashedPw = !PasswordUtilities.hash(admin.password)

        if (!adminUserExisting) {

            !AppUserRepo.create(
                AppUserRecord(
                    id = SYSTEM_USER,
                    email = admin.email,
                    firstname = "System",
                    lastname = "User",
                    password = hashedPw,
                    createdBy = SYSTEM_USER,
                    updatedBy = SYSTEM_USER,
                )
            ).orDie()
        } else {

            !AppUserRepo.update(SYSTEM_USER) {
                email = admin.email
                password = hashedPw
                updatedAt = LocalDateTime.now()
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
                    createdBy = SYSTEM_USER,
                    updatedBy = SYSTEM_USER,
                )
            ).orDie()
        }

        if (!adminUserExisting || !adminRoleExisting) {
            !AppUserHasRoleRepo.create(SYSTEM_USER, listOf(ADMIN_ROLE)).orDie()
        }

        // Add missing privileges

        val privilegeRecords = !PrivilegeRepo.all()
        !PrivilegeRepo.create(
            Privilege.entries
                .filter { p ->
                    privilegeRecords.none {
                        it.action == p.action.name
                            && it.resource == p.resource.name
                            && it.scope == p.scope.name
                    }
                }
        )

        App.unit
    }
        .transact()
        .unsafeRunSync(env)
        .getOrNullLogError { }
}