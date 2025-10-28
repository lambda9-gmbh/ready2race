package de.lambda9.ready2race.backend.app.webDAV.entity

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.control.AppUserHasRoleRepo
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.auth.control.PrivilegeRepo
import de.lambda9.ready2race.backend.app.club.control.ClubRepo
import de.lambda9.ready2race.backend.app.role.control.RoleHasPrivilegeRepo
import de.lambda9.ready2race.backend.app.role.control.RoleRepo
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVExportService
import de.lambda9.ready2race.backend.app.webDAV.boundary.WebDAVService.getWebDavDataJsonFileName
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.WebdavExportDataRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie

data class DataUsersExport(
    val appUsers: JsonNode,
    val roles: JsonNode,
    val privileges: JsonNode,
    val roleHasPrivileges: JsonNode,
    val appUserHasRoles: JsonNode,
    val clubs: JsonNode,
) : WebDAVExportData {
    companion object {
        fun createExportFile(
            record: WebdavExportDataRecord
        ): App<WebDAVError.WebDAVInternError, File> = KIO.comprehension {

            val appUsers = !AppUserRepo.getAllIdsExceptSystemAdmin().orDie()
            val appUsersJson = !AppUserRepo.getAllExceptSystemAdminAsJson().orDie()

            val roles = !RoleRepo.getAllIdsExceptStatic().orDie()
            val rolesJson = !RoleRepo.getAllExceptStaticAsJson().orDie()

            val privilegesJson = !PrivilegeRepo.allAsJson().orDie()
            val roleHasPrivilegesJson = !RoleHasPrivilegeRepo.getByRolesAsJson(roles).orDie()
            val appUserHasRolesJson = !AppUserHasRoleRepo.getByUsersAsJson(appUsers).orDie()
            val clubsJson = !ClubRepo.allAsJson().orDie()

            val json = !WebDAVExportService.serializeDataExportNew(
                record,
                mapOf(
                    "appUsers" to appUsersJson,
                    "roles" to rolesJson,
                    "privileges" to privilegesJson,
                    "roleHasPrivileges" to roleHasPrivilegesJson,
                    "appUserHasRoles" to appUserHasRolesJson,
                    "clubs" to clubsJson
                )
            )

            KIO.ok(File(name = getWebDavDataJsonFileName(WebDAVExportType.DB_USERS), bytes = json))
        }

        fun importData(data: DataUsersExport): App<WebDAVError.WebDAVImportNextError, Unit> = KIO.comprehension {

            // APP USER & CLUB (circular references - handled with two-phase import)

            val clubRecords = !ClubRepo.parseJsonToRecord(data.clubs.toString()).orDie()

            val overlappingClubs = !ClubRepo.getOverlapIds(clubRecords.map { it.id }).orDie()
            val filteredClubRecords = clubRecords.filter { !overlappingClubs.contains(it.id) }

            // Save original user references before clearing them
            val clubUserRefs = filteredClubRecords.associate { club ->
                club.id to Pair(club.createdBy, club.updatedBy)
            }

            // Create new CLUB records with cleared user references for initial insert
            val filteredClubRecordsWithoutUserRefs = filteredClubRecords.map { club ->
                ClubRecord(
                    id = club.id,
                    name = club.name,
                    createdAt = club.createdAt,
                    createdBy = null,
                    updatedAt = club.updatedAt,
                    updatedBy = null
                )
            }

            if (filteredClubRecordsWithoutUserRefs.isNotEmpty()) {
                !ClubRepo.create(filteredClubRecordsWithoutUserRefs).orDie()
            }


            // Create USERS
            val appUserRecords = !AppUserRepo.parseJsonToRecord(data.appUsers.toString()).orDie()

            val overlappingAppUsers = !AppUserRepo.getOverlapIds(appUserRecords.map { it.id }).orDie()
            val filteredAppUserRecords = appUserRecords.filter { !overlappingAppUsers.contains(it.id) }

            val overlappingEmails = !AppUserRepo.getOverlappingEmails(filteredAppUserRecords.map { it.email }).orDie()
            !KIO.failOn(overlappingEmails.isNotEmpty()) { WebDAVError.EmailExistingWithOtherId(emails = overlappingEmails) }

            if (filteredAppUserRecords.isNotEmpty()) {
                !AppUserRepo.insert(filteredAppUserRecords).orDie()
            }


            // Update CLUBS with correct created_by/updated_by references
            if (filteredClubRecordsWithoutUserRefs.isNotEmpty() && clubUserRefs.isNotEmpty()) {
                !ClubRepo.updateUserReferences(filteredClubRecordsWithoutUserRefs, clubUserRefs).orDie()
            }


            // ROLES
            !RoleRepo.insertJsonData(data.roles.toString()).orDie()


            // PRIVILEGES (not created - only for read purposes) - since privilege ids are generated randomly we need to associate the ones from the import with the ones from the database
            val privilegeRecords = !PrivilegeRepo.parseJsonToRecord(data.privileges.toString()).orDie()

            val privileges = !PrivilegeRepo.all().orDie()
            val unknownPrivileges =
                privilegeRecords.filter { dataPriv -> privileges.none { it.action == dataPriv.action && it.resource == dataPriv.resource && it.scope == dataPriv.scope } }
            !KIO.failOn(unknownPrivileges.isNotEmpty()) {
                WebDAVError.UnknownPrivilege(unknownPrivileges.map {
                    Triple(
                        it.action,
                        it.resource,
                        it.scope
                    )
                })
            }
            val privilegeIdsOldToNew = privilegeRecords.associate { priv ->
                priv.id to privileges.first { it.action == priv.action && it.resource == priv.resource && it.scope == priv.scope }.id
            }


            // ROLE HAS PRIVILEGE (change the old privilege id to the new one)
            val roleHasPrivilegeRecords =
                !RoleHasPrivilegeRepo.parseJsonToRecord(data.roleHasPrivileges.toString()).orDie()
            val roleHasPrivOverlaps = !RoleHasPrivilegeRepo
                .getOverlaps(roleHasPrivilegeRecords.map { it.role to privilegeIdsOldToNew[it.privilege]!! }) // Use the new id of the privilege
                .orDie()

            val roleHasPrivilegeRecordsWithNewPriv = roleHasPrivilegeRecords
                .filter { roleHasPriv -> roleHasPrivOverlaps.none { it.role == roleHasPriv.role && it.privilege == roleHasPriv.privilege } }
                .map {
                    it.apply {
                        privilege = privilegeIdsOldToNew[privilege]!!
                    }
                }

            if (roleHasPrivilegeRecordsWithNewPriv.isNotEmpty()) {
                !RoleHasPrivilegeRepo.create(roleHasPrivilegeRecords).orDie()
            }

            // APP USER HAS ROLE
            !AppUserHasRoleRepo.insertJsonData(data.appUserHasRoles.toString()).orDie()

            unit
        }
    }
}