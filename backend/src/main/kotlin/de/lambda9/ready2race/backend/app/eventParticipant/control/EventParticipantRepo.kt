package de.lambda9.ready2race.backend.app.eventParticipant.control

import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.tables.records.EventParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_PARTICIPANT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object EventParticipantRepo {

    fun create(record: EventParticipantRecord) = EVENT_PARTICIPANT.insert(record)

    fun updateAccessToken(eventId: UUID, participantID: UUID, newToken: String) = Jooq.query {

        update(EVENT_PARTICIPANT)
            .set(EVENT_PARTICIPANT.ACCESS_TOKEN, newToken)
            .where(EVENT_PARTICIPANT.EVENT.eq(eventId))
            .and(EVENT_PARTICIPANT.PARTICIPANT.eq(participantID))
            .execute()

        Unit
    }

    fun exists(eventId: UUID, participantID: UUID) = EVENT_PARTICIPANT.exists { EVENT.eq(eventId).and(PARTICIPANT.eq(participantID)) }

}