package de.lambda9.ready2race.backend.app.auth.control

import de.lambda9.ready2race.backend.afterNow
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.entity.LoginDto
import de.lambda9.ready2race.backend.app.auth.entity.PrivilegeDto
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserSessionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.PrivilegeRecord
import de.lambda9.ready2race.backend.security.RandomUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import kotlin.time.Duration

fun AppUserWithPrivilegesRecord.loginDto(): App<Nothing, LoginDto> =
    privileges!!.toList().traverse { it!!.toPrivilegeDto() }.map {
        LoginDto(
            id = id!!,
            clubId = club,
            privileges = it
        )
    }

fun AppUserWithPrivilegesRecord.toSession(
    lifetime: Duration,
): App<Nothing, AppUserSessionRecord> =
    KIO.ok(
        AppUserSessionRecord(
            token = RandomUtilities.token(),
            appUser = id!!,
            expiresAt = lifetime.afterNow(),
            createdAt = LocalDateTime.now(),
        )
    )

fun PrivilegeRecord.toPrivilegeDto(): App<Nothing, PrivilegeDto> =
    KIO.ok(
        PrivilegeDto(
            id = id,
            action = action,
            resource = resource,
            scope = scope
        )
    )
