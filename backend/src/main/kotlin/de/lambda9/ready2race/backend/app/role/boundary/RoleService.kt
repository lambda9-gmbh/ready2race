package de.lambda9.ready2race.backend.app.role.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.control.PrivilegeRepo
import de.lambda9.ready2race.backend.app.role.control.RoleHasPrivilegeRepo
import de.lambda9.ready2race.backend.app.role.control.RoleRepo
import de.lambda9.ready2race.backend.app.role.control.toDto
import de.lambda9.ready2race.backend.app.role.control.toRecord
import de.lambda9.ready2race.backend.app.role.entity.RoleDto
import de.lambda9.ready2race.backend.app.role.entity.RoleError
import de.lambda9.ready2race.backend.app.role.entity.RoleRequest
import de.lambda9.ready2race.backend.app.role.entity.RoleWithPrivilegesSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.RoleHasPrivilegeRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object RoleService {

    fun addRole(
        request: RoleRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        val roleId = !RoleRepo.create(record).orDie()

        val records = request.privileges.map {
            RoleHasPrivilegeRecord(
                role = roleId,
                privilege = it
            )
        }

        !RoleHasPrivilegeRepo.create(records).orDie()

        KIO.ok(
            ApiResponse.Created(roleId)
        )
    }

    fun page(
        params: PaginationParameters<RoleWithPrivilegesSort>,
    ): App<Nothing, ApiResponse.Page<RoleDto, RoleWithPrivilegesSort>> = KIO.comprehension {
        val total = !RoleRepo.countWithPrivileges(params.search).orDie()
        val page = !RoleRepo.pageWithPrivileges(params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun updateRole(
        id: UUID,
        request: RoleRequest,
        userId: UUID,
    ): App<RoleError, ApiResponse.NoData> = KIO.comprehension {
        val record = !RoleRepo.get(id).orDie()
            .onNullFail { RoleError.NotFound }
            .failIf(condition = { it.static }) { RoleError.Static }
        !RoleRepo.update(record) {
            name = request.name
            description = request.description
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()

        // TODO @Incomplete: check privilege Ids for better error than 500!

        val records = request.privileges.map {
            RoleHasPrivilegeRecord(
                role = id,
                privilege = it
            )
        }

        !RoleHasPrivilegeRepo.deleteByRole(id).orDie()
        !RoleHasPrivilegeRepo.create(records).orDie()

        noData
    }


    fun deleteRole(
        id: UUID,
    ): App<RoleError, ApiResponse.NoData> = KIO.comprehension {
        val record = !RoleRepo.get(id).orDie()
            .onNullFail { RoleError.NotFound }
            .failIf(condition = { it.static }) { RoleError.Static }
        record.delete() // todo: @style  -> Repo

        noData
    }

    fun checkAssignable(
        roles: List<UUID>
    ): App<RoleError, Unit> = KIO.comprehension {
        if (roles.isEmpty()) {
            KIO.unit
        } else {
            val found = !RoleRepo.getIfExist(roles).orDie()
            val notFound = roles.filter { id -> found.none { it.id == id } }
            val notAssignable = found.filter { it.static }.map { it.id }

            if (notFound.isNotEmpty() || notAssignable.isNotEmpty()) {
                KIO.fail(RoleError.CannotAssignRoles(notFound, notAssignable))
            } else {
                KIO.unit
            }
        }
    }
}