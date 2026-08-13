package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserInvitationToEmailRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_INVITATION_TO_EMAIL
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object AppUserInvitationToEmailRepo {

    fun create(record: AppUserInvitationToEmailRecord) = APP_USER_INVITATION_TO_EMAIL.insert(record)

    /**
     * Auf der Tabelle liegt ein eindeutiger Index auf app_user_invitation - beim erneuten
     * Verschicken darf deshalb keine zweite Zeile entstehen, sondern die vorhandene muss auf
     * die neue Mail zeigen.
     */
    fun upsert(record: AppUserInvitationToEmailRecord): JIO<Int> = Jooq.query {
        val updated = update(APP_USER_INVITATION_TO_EMAIL)
            .set(APP_USER_INVITATION_TO_EMAIL.EMAIL, record.email)
            .where(APP_USER_INVITATION_TO_EMAIL.APP_USER_INVITATION.eq(record.appUserInvitation))
            .execute()

        if (updated == 0) {
            insertInto(APP_USER_INVITATION_TO_EMAIL).set(record).execute()
        } else {
            updated
        }
    }

}
