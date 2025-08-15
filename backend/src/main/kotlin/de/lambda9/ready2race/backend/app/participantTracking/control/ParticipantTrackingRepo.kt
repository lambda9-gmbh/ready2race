package de.lambda9.ready2race.backend.app.participantTracking.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.ParticipantTrackingView
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantTrackingViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.impl.DSL
import java.util.*

object ParticipantTrackingRepo {

    private fun ParticipantTrackingView.searchFields() = listOf(FIRSTNAME, LASTNAME, CLUB_NAME, EXTERNAL_CLUB_NAME)


    fun insert(record: ParticipantTrackingRecord) = PARTICIPANT_TRACKING.insertReturning(record) { ID }

    fun get(participantId: UUID, eventId: UUID) = PARTICIPANT_TRACKING_VIEW.select {
        PARTICIPANT_ID.eq(participantId).and(
            EVENT_ID.eq(eventId)
        )
    }

    fun count(
        search: String?,
        eventId: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): JIO<Int> = Jooq.query {
        with(PARTICIPANT_TRACKING_VIEW) {
            fetchCount(
                this, search.metaSearch(searchFields())
                    .and(EVENT_ID.eq(eventId))
                    .and(filterScopeForView(scope, user.club))
            )
        }
    }

    fun page(
        params: PaginationParameters<ParticipantTrackingSort>,
        eventId: UUID,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): JIO<List<ParticipantTrackingViewRecord>> = Jooq.query {
        with(PARTICIPANT_TRACKING_VIEW) {
            selectFrom(this)
                .page(params, searchFields()) {
                    DSL.and(
                        EVENT_ID.eq(eventId)
                            .and(filterScopeForView(scope, user.club))
                    )
                }
                .fetch()
        }
    }


    private fun filterScopeForView(
        scope: Privilege.Scope,
        clubId: UUID?,
    ): Condition =
        if (scope == Privilege.Scope.OWN) PARTICIPANT_TRACKING_VIEW.CLUB_ID.eq(clubId) else DSL.trueCondition()
}