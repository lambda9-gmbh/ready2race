package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementForEventSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.generated.tables.ParticipantRequirementForEvent
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT_FOR_EVENT
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
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

}