package de.lambda9.ready2race.backend.app.competitionRegistration.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRegistrationNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION_NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object CompetitionRegistrationNamedParticipantRepo {

    fun create(record: CompetitionRegistrationNamedParticipantRecord) = COMPETITION_REGISTRATION_NAMED_PARTICIPANT.insert(record)

    fun existsByParticipantId(participantId: UUID) = COMPETITION_REGISTRATION_NAMED_PARTICIPANT.exists {
        PARTICIPANT.eq(participantId)
    }

    fun existsByParticipantIdAndCompetitionId(participantId: UUID?, competitionId: UUID?) = Jooq.query {
       fetchExists(
           COMPETITION_REGISTRATION_NAMED_PARTICIPANT
           .join(COMPETITION_REGISTRATION).on(COMPETITION_REGISTRATION.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION))
           .where(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT.eq(participantId)
           .and(COMPETITION_REGISTRATION.COMPETITION.eq(competitionId)))
       )
    }

    fun deleteAllByRegistrationId(registrationId: UUID) =
        COMPETITION_REGISTRATION_NAMED_PARTICIPANT.delete { COMPETITION_REGISTRATION.eq(registrationId) }
}