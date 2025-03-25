package de.lambda9.ready2race.backend.app.participant.control

import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.generated.tables.ParticipantForEvent
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_FOR_EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object ParticipantForEventRepo {

    private fun ParticipantForEvent.searchFields() = listOf(FIRSTNAME, LASTNAME, EXTERNAL_CLUB_NAME)

    fun count(
        search: String?,
        eventId: UUID
    ): JIO<Int> = Jooq.query {
        with(PARTICIPANT_FOR_EVENT) {
            fetchCount(
                this, search.metaSearch(searchFields()).and(EVENT_ID.eq(eventId))
            )
        }
    }

    fun page(
        params: PaginationParameters<ParticipantForEventSort>,
        eventId: UUID
    ): JIO<List<ParticipantForEventRecord>> = Jooq.query {
        with(PARTICIPANT_FOR_EVENT) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT_ID.eq(eventId)
                }
                .fetch()
        }
    }

    fun getParticipantsForEventWithMissingRequirement(
        eventId: UUID,
        participantRequirementId: UUID
    ): JIO<List<ParticipantForEventRecord>> = Jooq.query {
        with(PARTICIPANT_FOR_EVENT) {
            selectFrom(this)
                .where(
                    EVENT_ID.eq(eventId)
                        .andNotExists(
                            selectFrom(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT)
                                .where(
                                    PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.EVENT.eq(this.EVENT_ID)
                                        .and(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT.eq(this.PARTICIPANT_ID))
                                        .and(
                                            PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT_REQUIREMENT.eq(
                                                participantRequirementId
                                            )
                                        )
                                )
                        )
                )
                .fetch()
        }

    }


}