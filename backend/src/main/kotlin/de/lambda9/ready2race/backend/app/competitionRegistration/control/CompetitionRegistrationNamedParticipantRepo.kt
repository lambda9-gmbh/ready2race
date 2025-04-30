package de.lambda9.ready2race.backend.app.competitionRegistration.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION_NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.insert
import java.util.*

object CompetitionRegistrationNamedParticipantRepo {

    fun create(record: CompetitionRegistrationNamedParticipantRecord) = COMPETITION_REGISTRATION_NAMED_PARTICIPANT.insert(record)

    fun deleteAllByRegistrationId(registrationId: UUID) =
        COMPETITION_REGISTRATION_NAMED_PARTICIPANT.delete { COMPETITION_REGISTRATION.eq(registrationId) }
}