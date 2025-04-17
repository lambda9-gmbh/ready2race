package de.lambda9.ready2race.backend.app.role.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.control.toPrivilegeDto
import de.lambda9.ready2race.backend.app.role.entity.RoleDto
import de.lambda9.ready2race.backend.app.role.entity.RoleRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.EveryRoleWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RoleWithPrivilegesRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.UUID

fun RoleRequest.toRecord(userId: UUID): App<Nothing, RoleRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            RoleRecord(
                id = UUID.randomUUID(),
                name = name,
                description = description,
                static = false,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        }
    )

fun RoleWithPrivilegesRecord.toDto(): App<Nothing, RoleDto> =
    privileges!!.toList().traverse { it!!.toPrivilegeDto() }.map {
        RoleDto(
            id = id!!,
            name = name!!,
            description = description,
            privileges = it
        )
    }

fun EveryRoleWithPrivilegesRecord.toDto(): App<Nothing, RoleDto> =
    privileges!!.toList().traverse { it!!.toPrivilegeDto() }.map {
        RoleDto(
            id = id!!,
            name = name!!,
            description = description,
            privileges = it
        )
    }