package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserInvitationHasRoleRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION_HAS_ROLE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object AppUserInvitationHasRoleRepo {

    fun create(records: Collection<AppUserInvitationHasRoleRecord>) = APP_USER_INVITATION_HAS_ROLE.insert(records)
}