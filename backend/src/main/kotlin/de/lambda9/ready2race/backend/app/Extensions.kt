package de.lambda9.ready2race.backend.app

import de.lambda9.ready2race.backend.app.auth.boundary.AuthService.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.user.control.AppUserHasRoleRepo
import de.lambda9.ready2race.backend.database.ADMIN_ROLE
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie

fun AppUserWithPrivilegesRecord.isAdmin(): App<Nothing, Boolean> =
    AppUserHasRoleRepo.exists(this.id!!, ADMIN_ROLE).orDie()

fun AppUserWithPrivilegesRecord.validatePrivilege(
    action: Privilege.Action,
    resource: Privilege.Resource,
): App<AuthError, Privilege.Scope> = KIO.comprehension {

    privileges!!
        .filter { it!!.action == action.name && it.resource == resource.name }
        .map { Privilege.Scope.valueOf(it!!.scope!!) }
        .maxByOrNull { it.level }
        ?.let {
            KIO.ok(it)
        } ?: KIO.fail(AuthError.PrivilegeMissing)

}
