package de.lambda9.ready2race.backend.app.user.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.user.entity.AppUserDto
import de.lambda9.ready2race.backend.app.user.entity.AppUserProperties
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithRolesRecord
import de.lambda9.tailwind.core.KIO

fun AppUserWithRolesRecord.appUserDto(): App<Nothing, AppUserDto> =
    KIO.ok(
        AppUserDto(
            id = id!!,
            properties = AppUserProperties(
                firstname = firstname!!,
                lastname = lastname!!,
                email = email!!,
                roles = roles!!.map { it!! }
            )
        )
    )
