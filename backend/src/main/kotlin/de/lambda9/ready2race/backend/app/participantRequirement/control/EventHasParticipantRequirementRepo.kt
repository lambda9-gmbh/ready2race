package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.tailwind.jooq.Jooq
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.tables.records.EventHasParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_HAS_PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.insert
import java.util.*

object EventHasParticipantRequirementRepo {

    fun create(record: EventHasParticipantRequirementRecord) = EVENT_HAS_PARTICIPANT_REQUIREMENT.insert(record)

    fun exists(eventId: UUID, participantRequirementId: UUID, namedParticipantId: UUID? = null) =
        EVENT_HAS_PARTICIPANT_REQUIREMENT.exists {
            EVENT.eq(eventId)
                .and(PARTICIPANT_REQUIREMENT.eq(participantRequirementId))
                .and(
                    if (namedParticipantId != null)
                        NAMED_PARTICIPANT.eq(namedParticipantId)
                    else
                        NAMED_PARTICIPANT.isNull
                )
        }

    fun delete(eventId: UUID, participantRequirementId: UUID, namedParticipantId: UUID? = null) =
        EVENT_HAS_PARTICIPANT_REQUIREMENT.delete {
            EVENT.eq(eventId)
                .and(PARTICIPANT_REQUIREMENT.eq(participantRequirementId))
                .and(
                    if (namedParticipantId != null)
                        NAMED_PARTICIPANT.eq(namedParticipantId)
                    else
                        NAMED_PARTICIPANT.isNull
                )
        }

    fun getNamedParticipantId(eventId: UUID, participantRequirementId: UUID): JIO<UUID?> = Jooq.query {
        with(EVENT_HAS_PARTICIPANT_REQUIREMENT) {
            select(NAMED_PARTICIPANT)
                .from(this)
                .where(EVENT.eq(eventId))
                .and(PARTICIPANT_REQUIREMENT.eq(participantRequirementId))
                .fetchOne(NAMED_PARTICIPANT)
        }
    }

}