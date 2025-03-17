package de.lambda9.ready2race.backend.app.eventRegistration.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationOptionalFeeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION_OPTIONAL_FEE
import de.lambda9.ready2race.backend.database.insert

object CompetitionRegistrationOptionalFeeRepo {

    fun create(record: CompetitionRegistrationOptionalFeeRecord) = COMPETITION_REGISTRATION_OPTIONAL_FEE.insert(record)

}