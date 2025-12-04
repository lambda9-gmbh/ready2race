package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRegistrationCompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_REGISTRATION_COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.insert

object AppUserRegistrationCompetitionRegistrationRepo {

    fun create(records: List<AppUserRegistrationCompetitionRegistrationRecord>) =
        APP_USER_REGISTRATION_COMPETITION_REGISTRATION.insert(records)

}