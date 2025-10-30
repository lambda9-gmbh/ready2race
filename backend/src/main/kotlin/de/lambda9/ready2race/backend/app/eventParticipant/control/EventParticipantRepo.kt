package de.lambda9.ready2race.backend.app.eventParticipant.control

import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.tables.records.EventParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_PARTICIPANT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.selectOne
import java.util.UUID

object EventParticipantRepo {

    fun create(record: EventParticipantRecord) = EVENT_PARTICIPANT.insert(record)

    fun exists(eventId: UUID, participantID: UUID) = EVENT_PARTICIPANT.exists { EVENT.eq(eventId).and(PARTICIPANT.eq(participantID)) }

    fun getAccessToken(eventId: UUID, participantID: UUID) = EVENT_PARTICIPANT.selectOne({ ACCESS_TOKEN }) {
        EVENT.eq(eventId).and(PARTICIPANT.eq(participantID))
    }
}