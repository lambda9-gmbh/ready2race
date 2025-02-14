package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserInvitationToEmailRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION_TO_EMAIL
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object AppUserInvitationToEmailRepo {

    fun create(
        record: AppUserInvitationToEmailRecord
    ): JIO<Unit> = Jooq.query {
        with(APP_USER_INVITATION_TO_EMAIL) {
            insertInto(this).set(record).execute()
        }
    }
}