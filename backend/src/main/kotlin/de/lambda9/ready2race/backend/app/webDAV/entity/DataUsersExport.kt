package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.control.AppUserHasRoleRepo
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.club.control.ClubRepo
import de.lambda9.ready2race.backend.app.role.control.RoleHasPrivilegeRepo
import de.lambda9.ready2race.backend.app.role.control.RoleRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.app.webDAV.control.toExport
import de.lambda9.ready2race.backend.app.webDAV.control.toRecord
import de.lambda9.ready2race.backend.app.webDAV.control.toRecordWithoutUsers
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.ready2race.backend.security.RandomUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse

data class DataUsersExport(
    val appUsers: List<AppUserExport>,
    val roles: List<RoleExport>,
    val roleHasPrivileges: List<RoleHasPrivilegeExport>,
    val appUserHasRoles: List<AppUserHasRoleExport>,
    val clubs: List<ClubExport>,
) : WebDAVExportData {
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
            val clubs = !ClubRepo.all().orDie()
                .map { list -> !list.traverse { it.toExport() } }

            val exportData = DataUsersExport(
                appUsers = appUsers,
                roles = roles,
                roleHasPrivileges = roleHasPrivileges,
                appUserHasRoles = appUserHasRoles,
                clubs = clubs
            )

            val json = !WebDAVExportService.serializeDataExport(record, exportData)

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_USERS), bytes = json))
        }

        fun importData(data: DataUsersExport): App<WebDAVError.WebDAVImportNextError, Unit> = KIO.comprehension {
            try {
                // APP USER & CLUB (circular references - handled with two-phase import)

                // Create Clubs with user references = null
                val overlappingClubs = !ClubRepo.getOverlapIds(data.clubs.map { it.id }).orDie()
                val filteredClubs = data.clubs.filter { clubData -> !overlappingClubs.any { it == clubData.id } }
                val clubRecordsWithoutUserRefs = !filteredClubs.traverse { it.toRecordWithoutUsers() }
                !ClubRepo.create(clubRecordsWithoutUserRefs).orDie()

                // Create users
                val appUserOverlaps = !AppUserRepo.getOverlapIds(data.appUsers.map { it.id }).orDie()
                val appUserRecords = !data.appUsers
                    .filter { appUserData -> !appUserOverlaps.any { it == appUserData.id } }
                    .traverse { it.toRecord(password = RandomUtilities.token()) } // Random new password
                !AppUserRepo.getEmailsExisting(appUserRecords.map { it.email }).orDie()
                    .onTrueFail { WebDAVError.EmailExistingWithOtherId }

                !AppUserRepo.insert(appUserRecords).orDie()

                // Update clubs with correct created_by/updated_by references
                val clubUserRefs = filteredClubs.associate { club ->
                    club.id to Pair(club.createdBy, club.updatedBy)
                }
                !ClubRepo.updateUserReferences(clubRecordsWithoutUserRefs, clubUserRefs).orDie()


                // ROLE
                val existingRoles = !RoleRepo.getIfExist(data.roles.map { it.id }).orDie()
                val roleRecords = !data.roles
                    .filter { roleData -> !existingRoles.any { it.id == roleData.id } }
                    .traverse { it.toRecord() }
                !RoleRepo.create(roleRecords).orDie()

                // ROLE HAS PRIVILEGE
                val roleHasPrivOverlaps = !RoleHasPrivilegeRepo
                    .getOverlaps(data.roleHasPrivileges.map { it.role to it.privilege }).orDie()
                val roleHasPrivilegeRecords = !data.roleHasPrivileges
                    .filter { roleHasPriv -> !roleHasPrivOverlaps.any { it.role == roleHasPriv.role && it.privilege == roleHasPriv.privilege } }
                    .traverse { it.toRecord() }
                !RoleHasPrivilegeRepo.create(roleHasPrivilegeRecords).orDie()

                // APP USER HAS ROLE
                val appUserHasRoleOverlaps =
                    !AppUserHasRoleRepo.getOverlaps(data.appUserHasRoles.map { it.appUser to it.role }).orDie()
                val appUserHasRoleRecords = !data.appUserHasRoles
                    .filter { userHasRole -> !appUserHasRoleOverlaps.any { it.appUser == userHasRole.appUser && it.role == userHasRole.role } }
                    .traverse { it.toRecord() }
                !AppUserHasRoleRepo.create(appUserHasRoleRecords).orDie()

            } catch (ex: Exception) {
                return@comprehension KIO.fail(WebDAVError.Unexpected)
            }
            unit
        }
    }
}