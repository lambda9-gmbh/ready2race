package de.lambda9.ready2race.backend.app.webDAV.entity.users

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.control.AppUserHasRoleRepo
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.auth.control.PrivilegeRepo
import de.lambda9.ready2race.backend.app.club.control.ClubRepo
import de.lambda9.ready2race.backend.app.role.control.RoleHasPrivilegeRepo
import de.lambda9.ready2race.backend.app.role.control.RoleRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.control.toRecordWithoutUsers
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVError
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportData
import de.lambda9.ready2race.backend.app.webDAV.entity.WebDAVExportType
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.security.RandomUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse

data class DataUsersExport(
    val appUsers: List<AppUserExport>,
    val roles: List<RoleExport>,
    val privileges: List<PrivilegeExport>,
    val roleHasPrivileges: List<RoleHasPrivilegeExport>,
    val appUserHasRoles: List<AppUserHasRoleExport>,
    val clubs: List<ClubExport>,
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {
            val appUsers = !AppUserRepo.getAllExceptSystemAdmin().orDie()
                .andThen { list -> list.traverse { it.toExport() } }
            val roles = !RoleRepo.getAllExceptStatic().orDie()
                .andThen { list -> list.traverse { it.toExport() } }
            val privileges = !PrivilegeRepo.all().orDie()
                .andThen { list -> list.traverse { it.toExport() } }
            val roleHasPrivileges = !RoleHasPrivilegeRepo.getByRoles(roles.map { it.id }).orDie()
                .andThen { list -> list.traverse { it.toExport() } }
            val appUserHasRoles = !AppUserHasRoleRepo.getByUsers(appUsers.map { it.id }).orDie()
                .andThen { list -> list.traverse { it.toExport() } }
            val clubs = !ClubRepo.all().orDie()
                .andThen { list -> list.traverse { it.toExport() } }

            val exportData = DataUsersExport(
                appUsers = appUsers,
                roles = roles,
                privileges = privileges,
                roleHasPrivileges = roleHasPrivileges,
                appUserHasRoles = appUserHasRoles,
                clubs = clubs
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_USERS), bytes = json))
        }

        fun importData(data: DataUsersExport): App<WebDAVError.WebDAVImportNextError, Unit> = KIO.comprehension {
            // APP USER & CLUB (circular references - handled with two-phase import)

            // Create Clubs with user references = null
            val overlappingClubs = !ClubRepo.getOverlapIds(data.clubs.map { it.id }).orDie()
            val filteredClubs = data.clubs.filter { !overlappingClubs.contains(it.id) }
            val clubRecordsWithoutUserRefs = !filteredClubs.traverse { it.toRecordWithoutUsers() }

            if (clubRecordsWithoutUserRefs.isNotEmpty()) {
                !ClubRepo.create(clubRecordsWithoutUserRefs).orDie()
            }

            // Create users
            val appUserOverlaps = !AppUserRepo.getOverlapIds(data.appUsers.map { it.id }).orDie()
            val appUserRecords = !data.appUsers
                .filter { !appUserOverlaps.contains(it.id) }
                .traverse { it.toRecord(password = RandomUtilities.token()) } // Random new password

            val overlappingEmails = !AppUserRepo.getOverlappingEmails(appUserRecords.map { it.email }).orDie()
            !KIO.failOn(overlappingEmails.isNotEmpty()) { WebDAVError.EmailExistingWithOtherId(emails = overlappingEmails) }

            if (appUserRecords.isNotEmpty()) {
                !AppUserRepo.insert(appUserRecords).orDie()
            }

            // Update clubs with correct created_by/updated_by references
            val clubUserRefs = filteredClubs.associate { club ->
                club.id to Pair(club.createdBy, club.updatedBy)
            }

            if (clubRecordsWithoutUserRefs.isNotEmpty()) {
                !ClubRepo.updateUserReferences(clubRecordsWithoutUserRefs, clubUserRefs).orDie()
            }

            // ROLE
            val overlappingRoles = !RoleRepo.getOverlapIds(data.roles.map { it.id }).orDie()
            val roleRecords = !data.roles
                .filter { !overlappingRoles.contains(it.id) }
                .traverse { it.toRecord() }

            if (roleRecords.isNotEmpty()) {
                !RoleRepo.create(roleRecords).orDie()
            }

            // Privileges - since privilege ids are generated randomly we need to associate the ones from the import with the ones from the database
            val privileges = !PrivilegeRepo.all().orDie()
            val unknownPrivileges =
                data.privileges.filter { dataPriv -> privileges.none { it.action == dataPriv.action && it.resource == dataPriv.resource && it.scope == dataPriv.scope } }
            !KIO.failOn(unknownPrivileges.isNotEmpty()) {
                WebDAVError.UnknownPrivilege(unknownPrivileges.map {
                    Triple(
                        it.action,
                        it.resource,
                        it.scope
                    )
                })
            }
            val privilegeIdsOldToNew = data.privileges.associate { priv ->
                priv.id to privileges.first { it.action == priv.action && it.resource == priv.resource && it.scope == priv.scope }.id
            }


            // ROLE HAS PRIVILEGE
            val roleHasPrivOverlaps = !RoleHasPrivilegeRepo
                .getOverlaps(data.roleHasPrivileges.map { it.role to privilegeIdsOldToNew[it.privilege]!! }) // Use the new id of the privilege
                .orDie()
            val roleHasPrivilegeRecords = !data.roleHasPrivileges
                .filter { roleHasPriv -> roleHasPrivOverlaps.none { it.role == roleHasPriv.role && it.privilege == roleHasPriv.privilege } }
                .traverse { it.toRecord() }

            if (roleHasPrivilegeRecords.isNotEmpty()) {
                !RoleHasPrivilegeRepo.create(roleHasPrivilegeRecords).orDie()
            }

            // APP USER HAS ROLE
            val appUserHasRoleOverlaps =
                !AppUserHasRoleRepo.getOverlaps(data.appUserHasRoles.map { it.appUser to it.role }).orDie()
            val appUserHasRoleRecords = !data.appUserHasRoles
                .filter { userHasRole -> appUserHasRoleOverlaps.none { it.appUser == userHasRole.appUser && it.role == userHasRole.role } }
                .traverse { it.toRecord() }

            if (appUserHasRoleRecords.isNotEmpty()) {
                !AppUserHasRoleRepo.create(appUserHasRoleRecords).orDie()
            }

            unit
        }
    }
}