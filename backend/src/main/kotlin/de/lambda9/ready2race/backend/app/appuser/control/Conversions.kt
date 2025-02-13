package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.afterNow
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.entity.AppUserDto
import de.lambda9.ready2race.backend.app.appuser.entity.InviteRequest
import de.lambda9.ready2race.backend.app.appuser.entity.RegisterRequest
import de.lambda9.ready2race.backend.app.role.control.toDto
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.security.PasswordUtilities
import de.lambda9.ready2race.backend.security.RandomUtilities
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration

fun AppUserWithRolesRecord.appUserDto(): App<Nothing, AppUserDto> =
    roles!!.toList().forEachM { it!!.toDto() }.map {
        AppUserDto(
            id = id!!,
            firstname = firstname!!,
            lastname = lastname!!,
            email = email!!,
            roles = it
        )
    }

fun InviteRequest.toRecord(inviterId: UUID, lifeTime: Duration): App<Nothing, AppUserInvitationRecord> =
    KIO.ok(
        AppUserInvitationRecord(
            token = RandomUtilities.token(),
            email = email,
            firstname = firstname,
            lastname = lastname,
            language = language.name,
            expiresAt = lifeTime.afterNow(),
            createdAt = LocalDateTime.now(),
            createdBy = inviterId
        )
    )

fun RegisterRequest.toRecord(lifeTime: Duration): App<Nothing, AppUserRegistrationRecord> =
    PasswordUtilities.hash(password).map {
        AppUserRegistrationRecord(
            token = RandomUtilities.token(),
            email = email,
            password = it,
            firstname = firstname,
            lastname = lastname,
            language = language.name,
            expiresAt = lifeTime.afterNow(),
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

fun AppUserInvitationWithRolesRecord.toAppUser(password: String): App<Nothing, AppUserRecord> =
    PasswordUtilities.hash(password).map { hashed ->
        LocalDateTime.now().let { now ->
            AppUserRecord(
                id = UUID.randomUUID(),
                email = email!!,
                password = hashed,
                firstname = firstname!!,
                lastname = lastname!!,
                language = language!!,
                createdAt = now,
                createdBy = createdBy,
                updatedAt = now,
                updatedBy = createdBy
            )
        }
    }