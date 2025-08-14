package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantHasRequirementForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CHECKED_PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object ParticipantHasRequirementForEventRepo {

    fun create(record: ParticipantHasRequirementForEventRecord) = PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.insert(record)

    fun update(
        participantId: UUID,
        eventId: UUID,
        participantRequirementId: UUID,
        f: ParticipantHasRequirementForEventRecord.() -> Unit,
    ) = PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.update(f) {
        DSL.and(
            PARTICIPANT.eq(participantId),
            EVENT.eq(eventId),
            PARTICIPANT_REQUIREMENT.eq(participantRequirementId),
        )
    }

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

    fun getApprovedParticipantIds(eventId: UUID, participantRequirementId: UUID) = Jooq.query {
        with(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT) {
            select(PARTICIPANT)
                .from(this)
                .where(EVENT.eq(eventId))
                .and(PARTICIPANT_REQUIREMENT.eq(participantRequirementId))
                .fetchInto(UUID::class.java)
        }
    }

    fun deleteWhereParticipantNotInList(
        eventId: UUID,
        participantRequirementId: UUID,
        approvedParticipants: List<UUID>
    ) = PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.delete {
        EVENT.eq(eventId).and(PARTICIPANT_REQUIREMENT.eq(participantRequirementId))
            .and(PARTICIPANT.notIn(approvedParticipants))
    }

    fun getApprovedRequirements(eventId: UUID, participantId: UUID) =
        CHECKED_PARTICIPANT_REQUIREMENT.select {
            DSL.and(
                EVENT.eq(eventId),
                PARTICIPANT.eq(participantId)
            )
        }

}