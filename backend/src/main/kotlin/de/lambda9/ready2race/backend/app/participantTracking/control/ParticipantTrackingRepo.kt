package de.lambda9.ready2race.backend.app.participantTracking.control

import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.selectOne
import java.util.*

object ParticipantTrackingRepo {

    fun insert(record: ParticipantTrackingRecord) = PARTICIPANT_TRACKING.insertReturning(record) { ID }

    fun get(participantId: UUID, eventId: UUID) = PARTICIPANT_TRACKING_VIEW.select {
        PARTICIPANT_ID.eq(participantId).and(
            EVENT_ID.eq(eventId)
        )
    }

    fun getCurrentStatus(participantId: UUID, eventId: UUID) =
        PARTICIPANT_TRACKING.selectOne({ SCAN_TYPE }) { PARTICIPANT.eq(participantId).and(EVENT.eq(eventId)) }

}