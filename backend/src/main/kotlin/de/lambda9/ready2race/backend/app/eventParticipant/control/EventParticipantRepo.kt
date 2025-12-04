package de.lambda9.ready2race.backend.app.eventParticipant.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.tables.records.EventParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_PARTICIPANT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.UUID

object EventParticipantRepo {

    fun create(record: EventParticipantRecord) = EVENT_PARTICIPANT.insert(record)

    fun upsertAccessToken(eventId: UUID, participantId: UUID, newToken: String) =
        EVENT_PARTICIPANT.delete {
            DSL.and(
                EVENT.eq(eventId),
                PARTICIPANT.eq(participantId)
            )
        }.andThen {
            EVENT_PARTICIPANT.insert(
                EventParticipantRecord(
                    event = eventId,
                    participant = participantId,
                    accessToken = newToken,
                )
            )
        }

    fun exists(eventId: UUID, participantID: UUID) = EVENT_PARTICIPANT.exists { EVENT.eq(eventId).and(PARTICIPANT.eq(participantID)) }

    fun getByToken(accessToken: String) = EVENT_PARTICIPANT.selectOne { ACCESS_TOKEN.eq(accessToken) }

}