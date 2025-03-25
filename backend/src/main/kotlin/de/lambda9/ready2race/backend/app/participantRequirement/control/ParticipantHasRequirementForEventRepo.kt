package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantHasRequirementForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT
import de.lambda9.ready2race.backend.database.insert
import java.util.*

object ParticipantHasRequirementForEventRepo {

    fun create(record: ParticipantHasRequirementForEventRecord) = PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.insert(record)

    fun exists(eventId: UUID, participantRequirementId: UUID, participantId: UUID) =
        PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.exists {
            EVENT.eq(eventId).and(PARTICIPANT_REQUIREMENT.eq(participantRequirementId))
                .and(PARTICIPANT.eq(participantId))
        }

    fun delete(eventId: UUID, participantRequirementId: UUID, participantId: UUID) =
        PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.delete {
            EVENT.eq(eventId)
                .and(PARTICIPANT_REQUIREMENT.eq(participantRequirementId).and(PARTICIPANT.eq(participantId)))
        }

}