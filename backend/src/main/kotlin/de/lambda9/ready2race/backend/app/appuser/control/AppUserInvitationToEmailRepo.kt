package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserInvitationToEmailRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION_TO_EMAIL
import de.lambda9.ready2race.backend.database.insert

object AppUserInvitationToEmailRepo {

    fun create(record: AppUserInvitationToEmailRecord) = APP_USER_INVITATION_TO_EMAIL.insert(record)

}