package de.lambda9.ready2race.backend.app.appuser.control

import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRegistrationCompetitionRegistrationFeeRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserRegistrationCompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_REGISTRATION_COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER_REGISTRATION_COMPETITION_REGISTRATION_FEE
import de.lambda9.ready2race.backend.database.insert

object AppUserRegistrationCompetitionRegistrationFeeRepo {

    fun create(records: List<AppUserRegistrationCompetitionRegistrationFeeRecord>) =
        APP_USER_REGISTRATION_COMPETITION_REGISTRATION_FEE.insert(records)

}