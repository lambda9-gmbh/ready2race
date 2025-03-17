package de.lambda9.ready2race.backend.database

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.appuser.control.AppUserHasRoleRepo
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.auth.control.PrivilegeRepo
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.app.role.control.RoleHasPrivilegeRepo
import de.lambda9.ready2race.backend.app.role.control.RoleRepo
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrThrow
import de.lambda9.tailwind.jooq.transact
import java.time.LocalDateTime
import java.util.*

fun initializeDatabase(env: JEnv) {

    // instantiate now, so template files get read and checked
    EmailService

    KIO.comprehension {

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
        val allPrivileges = !PrivilegeRepo.all()

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
                    createdAt = now,
                    createdBy = SYSTEM_USER,
                    updatedAt = now,
                    updatedBy = SYSTEM_USER,
                )
            )
        }

        if (!adminUserExisting || !adminRoleExisting) {
            !AppUserHasRoleRepo.create(
                AppUserHasRoleRecord(
                    appUser = SYSTEM_USER,
                    role = ADMIN_ROLE,
                )
            )
        }

        val currentAdminPrivileges = !RoleHasPrivilegeRepo.getPrivilegesByRole(ADMIN_ROLE)
        !RoleHasPrivilegeRepo.create(
            allPrivileges.filter {
                currentAdminPrivileges.none { id -> id == it.id }
            }.map {
                RoleHasPrivilegeRecord(
                    role = ADMIN_ROLE,
                    privilege = it.id,
                )
            }
        )

        // Add default user role & privileges

        val userPrivileges = listOf(
            Privilege.ReadUserOwn,
        )

        val userRoleExisting = !RoleRepo.exists(USER_ROLE)

        if (!userRoleExisting) {
            !RoleRepo.create(
                RoleRecord(
                    id = USER_ROLE,
                    name = "User",
                    description = "Global user role",
                    static = true,
                    createdAt = now,
                    createdBy = SYSTEM_USER,
                    updatedAt = now,
                    updatedBy = SYSTEM_USER,
                )
            )
        }

        val currentUserPrivileges = !RoleHasPrivilegeRepo.getPrivilegesByRole(USER_ROLE)

        !RoleHasPrivilegeRepo.create(
            allPrivileges.filter {
                currentUserPrivileges.none { id ->
                    id == it.id
                } && userPrivileges.any { p ->
                    p.action.name == it.action && p.resource.name == it.resource && p.scope.name == it.scope
                }
            }.map {
                RoleHasPrivilegeRecord(
                    role = USER_ROLE,
                    privilege = it.id
                )
            }
        )

        App.unit
    }
        .transact()
        .unsafeRunSync(env)
        .getOrThrow()
}