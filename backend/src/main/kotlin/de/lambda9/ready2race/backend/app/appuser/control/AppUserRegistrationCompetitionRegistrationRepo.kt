package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRegistrationCompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_REGISTRATION_COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.select
import java.util.*

object AppUserRegistrationCompetitionRegistrationRepo {

    fun create(records: List<AppUserRegistrationCompetitionRegistrationRecord>) =
        APP_USER_REGISTRATION_COMPETITION_REGISTRATION.insert(records)

    fun getByAppUserRegistration(appUserRegistrationId: UUID) =
        APP_USER_REGISTRATION_COMPETITION_REGISTRATION.select { APP_USER_REGISTRATION.eq(appUserRegistrationId) }

    fun deleteByAppUserRegistration(appUserRegistrationId: UUID) =
        APP_USER_REGISTRATION_COMPETITION_REGISTRATION.delete { APP_USER_REGISTRATION.eq(appUserRegistrationId) }

}