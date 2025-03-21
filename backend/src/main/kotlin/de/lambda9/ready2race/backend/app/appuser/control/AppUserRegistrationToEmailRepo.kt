package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRegistrationToEmailRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_REGISTRATION_TO_EMAIL
import de.lambda9.ready2race.backend.database.insert

object AppUserRegistrationToEmailRepo {

    fun create(record: AppUserRegistrationToEmailRecord) = APP_USER_REGISTRATION_TO_EMAIL.insert(record)

}