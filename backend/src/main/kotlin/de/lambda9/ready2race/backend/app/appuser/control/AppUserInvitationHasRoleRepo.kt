package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserInvitationHasRoleRecord
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object AppUserInvitationHasRoleRepo {

    fun create(
        records: List<AppUserInvitationHasRoleRecord>,
    ): JIO<Int> = Jooq.query {
        batchInsert(records).execute().sum()
    }
}