package de.lambda9.ready2race.backend.app.participant.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.generated.tables.ParticipantForEvent
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_FOR_EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.impl.DSL
import java.util.*

object ParticipantForEventRepo {

    private fun ParticipantForEvent.searchFields() = listOf(FIRSTNAME, LASTNAME, EXTERNAL_CLUB_NAME)

    fun getByClub(
        clubId: UUID,
    ): JIO<List<ParticipantForEventRecord>> = Jooq.query {
        with(PARTICIPANT_FOR_EVENT) {
            selectFrom(this)
                .where(CLUB_ID.eq(clubId))
                .fetch()
        }
    }

    fun count(
        search: String?,
        eventId: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): JIO<Int> = Jooq.query {
        with(PARTICIPANT_FOR_EVENT) {
            fetchCount(
                this, search.metaSearch(searchFields())
                    .and(EVENT_ID.eq(eventId))
                    .and(filterScope(scope, user.club))
            )
        }
    }

    fun page(
        params: PaginationParameters<ParticipantForEventSort>,
        eventId: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): JIO<List<ParticipantForEventRecord>> = Jooq.query {
        with(PARTICIPANT_FOR_EVENT) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT_ID.eq(eventId)
                        .and(filterScope(scope, user.club))
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
                                        .and(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT.eq(this.ID))
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

    private fun filterScope(
        scope: Privilege.Scope,
        clubId: UUID?,
    ): Condition = if (scope == Privilege.Scope.OWN) PARTICIPANT_FOR_EVENT.CLUB_ID.eq(clubId) else DSL.trueCondition()

}