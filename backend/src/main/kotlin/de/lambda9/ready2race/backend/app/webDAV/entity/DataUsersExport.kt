package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.control.AppUserHasRoleRepo
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.role.control.RoleHasPrivilegeRepo
import de.lambda9.ready2race.backend.app.role.control.RoleRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse

data class DataUsersExport(
    val appUsers: List<AppUserExport>,
    val roles: List<RoleExport>,
    val roleHasPrivileges: List<RoleHasPrivilegeExport>,
    val appUserHasRoles: List<AppUserHasRoleExport>
) {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val appUsers = !AppUserRepo.getAllExceptSystemAdmin().orDie()
                .map { list -> !list.traverse { it.toExport() } }
            val roles = !RoleRepo.getAllExceptStatic().orDie()
                .map { list -> !list.traverse { it.toExport() } }
            val roleHasPrivileges = !RoleHasPrivilegeRepo.getByRoles(roles.map { it.id }).orDie()
                .map { list -> !list.traverse { it.toExport() } }
            val appUserHasRoles = !AppUserHasRoleRepo.getByUsers(appUsers.map { it.id }).orDie()
                .map { list -> !list.traverse { it.toExport() } }

            val exportData = DataUsersExport(
                appUsers = appUsers,
                roles = roles,
                roleHasPrivileges = roleHasPrivileges,
                appUserHasRoles = appUserHasRoles
            )

            val json = !WebDAVService.serializeDataExport(record, exportData, WebDAVExportType.DB_USERS)

            KIO.ok(File(name = "users.json", bytes = json))
        }
    }
}