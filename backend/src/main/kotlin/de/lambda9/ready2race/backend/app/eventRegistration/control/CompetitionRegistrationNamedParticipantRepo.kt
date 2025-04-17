package de.lambda9.ready2race.backend.app.eventRegistration.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION_NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.insert

object CompetitionRegistrationNamedParticipantRepo {

    fun create(record: CompetitionRegistrationNamedParticipantRecord) = COMPETITION_REGISTRATION_NAMED_PARTICIPANT.insert(record)

}