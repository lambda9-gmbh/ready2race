package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.tables.records.EventHasParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_HAS_PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.insert
import java.util.*

object EventHasParticipantRequirementRepo {

    fun create(record: EventHasParticipantRequirementRecord) = EVENT_HAS_PARTICIPANT_REQUIREMENT.insert(record)

    fun exists(eventId: UUID, participantRequirementId: UUID) = EVENT_HAS_PARTICIPANT_REQUIREMENT.exists {
        EVENT.eq(eventId).and(PARTICIPANT_REQUIREMENT.eq(participantRequirementId))
    }

    fun delete(eventId: UUID, participantRequirementId: UUID) = EVENT_HAS_PARTICIPANT_REQUIREMENT.delete {
        EVENT.eq(eventId).and(PARTICIPANT_REQUIREMENT.eq(participantRequirementId))
    }

}