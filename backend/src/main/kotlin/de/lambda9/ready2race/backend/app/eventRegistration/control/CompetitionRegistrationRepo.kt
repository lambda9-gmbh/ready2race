package de.lambda9.ready2race.backend.app.eventRegistration.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.insertReturning
import java.util.*

object CompetitionRegistrationRepo {

    fun create(record: CompetitionRegistrationRecord) = COMPETITION_REGISTRATION.insertReturning(record) { ID }

    fun deleteByEventRegistration(eventRegistrationId: UUID) =
        COMPETITION_REGISTRATION.delete { COMPETITION_REGISTRATION.EVENT_REGISTRATION.eq(eventRegistrationId) }

}