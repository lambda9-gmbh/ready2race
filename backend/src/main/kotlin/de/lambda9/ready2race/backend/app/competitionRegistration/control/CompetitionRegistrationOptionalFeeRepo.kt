package de.lambda9.ready2race.backend.app.competitionRegistration.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationOptionalFeeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION_OPTIONAL_FEE
import de.lambda9.ready2race.backend.database.insert
import java.util.*

object CompetitionRegistrationOptionalFeeRepo {

    fun create(record: CompetitionRegistrationOptionalFeeRecord) = COMPETITION_REGISTRATION_OPTIONAL_FEE.insert(record)

    fun deleteAllByRegistrationId(registrationId: UUID) =
        COMPETITION_REGISTRATION_OPTIONAL_FEE.delete { COMPETITION_REGISTRATION.eq(registrationId) }

}