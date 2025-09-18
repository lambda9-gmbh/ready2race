package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.tailwind.jooq.Jooq
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.ready2race.backend.database.generated.tables.records.EventHasParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_TEMPLATE
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_HAS_PARTICIPANT_REQUIREMENT
import java.util.*

object EventHasParticipantRequirementRepo {

    fun create(record: EventHasParticipantRequirementRecord) = EVENT_HAS_PARTICIPANT_REQUIREMENT.insert(record)

    fun create(records: List<EventHasParticipantRequirementRecord>) = EVENT_HAS_PARTICIPANT_REQUIREMENT.insert(records)

    fun getByEvent(eventId: UUID) = EVENT_HAS_PARTICIPANT_REQUIREMENT.select { EVENT.eq(eventId) }

    fun exists(eventId: UUID, participantRequirementId: UUID, namedParticipantId: UUID? = null) =
        EVENT_HAS_PARTICIPANT_REQUIREMENT.exists {
            EVENT.eq(eventId)
                .and(PARTICIPANT_REQUIREMENT.eq(participantRequirementId))
                .and(
                    namedParticipantId?.let { NAMED_PARTICIPANT.eq(it) }
                        ?: NAMED_PARTICIPANT.isNull
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

    fun getAsJson(eventId: UUID) = EVENT_HAS_PARTICIPANT_REQUIREMENT.selectAsJson { EVENT.eq(eventId) }

    fun insertJsonData(data: String) = EVENT_HAS_PARTICIPANT_REQUIREMENT.insertJsonData(data)

}