package de.lambda9.ready2race.backend.app.auth.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.entity.LoginDto
import de.lambda9.ready2race.backend.app.auth.entity.PrivilegeDto
import de.lambda9.ready2race.backend.app.isAdmin
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.PrivilegeRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.orDie

fun AppUserWithPrivilegesRecord.loginDto(): App<Nothing, LoginDto> = KIO.comprehension {

    val admin = !isAdmin()

    if (admin) {
        PrivilegeRepo.all().orDie().map { privilegeRecords ->
            LoginDto(
                privileges = !privilegeRecords.forEachM { it.toPrivilegeDto() }
            )
        }
    } else {
        privileges!!.toList().forEachM { it!!.toPrivilegeDto()}.map {
            LoginDto(
                privileges = it
            )
        }
    }
}

fun PrivilegeRecord.toPrivilegeDto(): App<Nothing, PrivilegeDto> =
    KIO.ok(
        PrivilegeDto(
            action = action!!,
            resource = resource!!,
            scope = scope!!
        )
    )
