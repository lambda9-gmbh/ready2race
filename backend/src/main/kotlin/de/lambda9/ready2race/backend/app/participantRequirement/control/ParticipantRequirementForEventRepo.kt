package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementForEventSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.generated.tables.ParticipantRequirementForEvent
import de.lambda9.ready2race.backend.database.generated.tables.records.EventHasParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_HAS_PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT_FOR_EVENT
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.database.select
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object ParticipantRequirementForEventRepo {

    private fun ParticipantRequirementForEvent.searchFields() = listOf(PARTICIPANT_REQUIREMENT_FOR_EVENT.NAME)

    fun count(
        search: String?,
        eventId: UUID,
        onlyActive: Boolean = false
    ): JIO<Int> = Jooq.query {
        with(PARTICIPANT_REQUIREMENT_FOR_EVENT)
        {
            fetchCount(
                this, search.metaSearch(searchFields()).and(
                    EVENT.eq(eventId)
                        .and(if (onlyActive) ACTIVE.isTrue else DSL.trueCondition())
                )
            )
        }
    }

    fun page(
        params: PaginationParameters<ParticipantRequirementForEventSort>,
        eventId: UUID,
        onlyActive: Boolean = false
    ): JIO<List<ParticipantRequirementForEventRecord>> = Jooq.query {
        with(PARTICIPANT_REQUIREMENT_FOR_EVENT) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId)
                        .and(if (onlyActive) ACTIVE.isTrue else DSL.trueCondition())
                }
                .fetch()
        }
    }

    fun get(
        eventId: UUID,
        onlyActive: Boolean = false,
        onlyForApp: Boolean = false,
    ): JIO<List<ParticipantRequirementForEventRecord>> = PARTICIPANT_REQUIREMENT_FOR_EVENT.select {
        EVENT.eq(eventId).and(if (onlyActive) ACTIVE.isTrue else DSL.trueCondition())
            .and(if (onlyForApp) CHECK_IN_APP.isTrue else DSL.trueCondition())
    }


    fun getRequirementsForNamedParticipant(
        eventId: UUID,
        namedParticipantId: UUID
    ): JIO<List<EventHasParticipantRequirementRecord>> = Jooq.query {
        selectFrom(EVENT_HAS_PARTICIPANT_REQUIREMENT)
            .where(
                EVENT_HAS_PARTICIPANT_REQUIREMENT.EVENT.eq(eventId)
                    .and(
                        EVENT_HAS_PARTICIPANT_REQUIREMENT.NAMED_PARTICIPANT.eq(namedParticipantId)
                            .or(EVENT_HAS_PARTICIPANT_REQUIREMENT.NAMED_PARTICIPANT.isNull)
                    )
            )
            .fetch()
    }

    fun assignRequirementToNamedParticipant(
        eventId: UUID,
        participantRequirementId: UUID,
        namedParticipantId: UUID?,
        qrCodeRequired: Boolean,
        createdBy: UUID
    ): JIO<EventHasParticipantRequirementRecord> = Jooq.query {
        insertInto(EVENT_HAS_PARTICIPANT_REQUIREMENT)
            .set(EVENT_HAS_PARTICIPANT_REQUIREMENT.EVENT, eventId)
            .set(EVENT_HAS_PARTICIPANT_REQUIREMENT.PARTICIPANT_REQUIREMENT, participantRequirementId)
            .set(EVENT_HAS_PARTICIPANT_REQUIREMENT.NAMED_PARTICIPANT, namedParticipantId)
            .set(EVENT_HAS_PARTICIPANT_REQUIREMENT.QR_CODE_REQUIRED, qrCodeRequired)
            .set(EVENT_HAS_PARTICIPANT_REQUIREMENT.CREATED_AT, DSL.currentLocalDateTime())
            .set(EVENT_HAS_PARTICIPANT_REQUIREMENT.CREATED_BY, createdBy)
            .onConflictDoNothing()
            .returning()
            .fetchOne()!!
    }

    fun updateQrCodeRequirement(
        eventId: UUID,
        participantRequirementId: UUID,
        namedParticipantId: UUID?,
        qrCodeRequired: Boolean
    ): JIO<Int> = Jooq.query {
        update(EVENT_HAS_PARTICIPANT_REQUIREMENT)
            .set(EVENT_HAS_PARTICIPANT_REQUIREMENT.QR_CODE_REQUIRED, qrCodeRequired)
            .where(
                EVENT_HAS_PARTICIPANT_REQUIREMENT.EVENT.eq(eventId)
                    .and(EVENT_HAS_PARTICIPANT_REQUIREMENT.PARTICIPANT_REQUIREMENT.eq(participantRequirementId))
                    .and(
                        if (namedParticipantId != null)
                            EVENT_HAS_PARTICIPANT_REQUIREMENT.NAMED_PARTICIPANT.eq(namedParticipantId)
                        else
                            EVENT_HAS_PARTICIPANT_REQUIREMENT.NAMED_PARTICIPANT.isNull
                    )
            )
            .execute()
    }

}