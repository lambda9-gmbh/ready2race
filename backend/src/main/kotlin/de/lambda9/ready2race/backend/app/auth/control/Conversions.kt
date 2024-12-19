package de.lambda9.ready2race.backend.app.auth.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.entity.LoginDto
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.isAdmin
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie

fun AppUserWithPrivilegesRecord.loginDto(): App<Nothing, LoginDto> = KIO.comprehension {

    val admin = !isAdmin()

    if (admin) {
        PrivilegeRepo.all().orDie().map {
            LoginDto(
                privilegesGlobal = it,
                privilegesBound = emptyList()
            )
        }
    } else {
        KIO.ok(
            LoginDto(
                privilegesGlobal = privilegesGlobal!!.map { Privilege.valueOf(it!!) },
                privilegesBound = privilegesBound!!.map { Privilege.valueOf(it!!) },
            )
        )
    }
}
