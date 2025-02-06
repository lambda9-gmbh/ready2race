package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.entity.AppUserDto
import de.lambda9.ready2race.backend.app.appuser.entity.RegisterRequest
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithRolesRecord
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.ready2race.backend.security.RandomUtilities
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun AppUserWithRolesRecord.appUserDto(): App<Nothing, AppUserDto> =
    KIO.ok(
        AppUserDto(
            id = id!!,
            firstname = firstname!!,
            lastname = lastname!!,
            email = email!!,
            roles = roles!!.map { it!! }
        )
    )

fun RegisterRequest.toRecord(): App<Nothing, AppUserRegistrationRecord> =
    PasswordUtilities.hash(password).map {
        AppUserRegistrationRecord(
            token = RandomUtilities.alphanumerical(),
            email = email,
            password = it,
            firstname = firstname,
            lastname = lastname,
            language = language.name,
            createdAt = LocalDateTime.now()
        )
    }

fun AppUserRegistrationRecord.toAppUser(): App<Nothing, AppUserRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            AppUserRecord(
                id = UUID.randomUUID(),
                email = email,
                password = password,
                firstname = firstname,
                lastname = lastname,
                language = language,
                createdAt = now,
                createdBy = SYSTEM_USER,
                updatedAt = now,
                updatedBy = SYSTEM_USER
            )
        }
    )