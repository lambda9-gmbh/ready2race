package de.lambda9.ready2race.backend.app

import de.lambda9.ready2race.backend.app.user.control.AppUserHasRoleRepo
import de.lambda9.ready2race.backend.database.ADMIN_ROLE
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.tailwind.core.extensions.kio.orDie

fun AppUserWithPrivilegesRecord.isAdmin(): App<Nothing, Boolean> =
    AppUserHasRoleRepo.exists(this.id!!, ADMIN_ROLE).orDie()