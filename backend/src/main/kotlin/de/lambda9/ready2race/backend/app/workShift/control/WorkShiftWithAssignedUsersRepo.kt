package de.lambda9.ready2race.backend.app.workShift.control

import de.lambda9.ready2race.backend.app.workShift.entity.WorkShiftWithAssignedUsersSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.generated.tables.WorkShiftWithAssignedUsers
import de.lambda9.ready2race.backend.database.generated.tables.records.WorkShiftWithAssignedUsersRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.WORK_SHIFT_HAS_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.WORK_SHIFT_WITH_ASSIGNED_USERS
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.*

object WorkShiftWithAssignedUsersRepo {

    private fun WorkShiftWithAssignedUsers.searchFields() = listOf(WORK_TYPE_NAME, TITLE)

    fun countByEvent(
        eventId: UUID,
        search: String?,
        timeFrom: LocalDateTime?,
        timeTo: LocalDateTime?
    ): JIO<Int> = Jooq.query {
        with(WORK_SHIFT_WITH_ASSIGNED_USERS) {
            fetchCount(
                this,
                DSL.and(
                    EVENT.eq(eventId),
                    filterTimeRange(timeFrom, timeTo),
                    search.metaSearch(searchFields())
                )
            )
        }
    }

    fun countByUser(
        userId: UUID,
        search: String?,
        timeFrom: LocalDateTime?,
        timeTo: LocalDateTime?
    ): JIO<Int> = Jooq.query {
        with(WORK_SHIFT_WITH_ASSIGNED_USERS) {
            fetchCount(
                this,
                DSL.and(
                    DSL.exists(
                        selectFrom(
                            WORK_SHIFT_HAS_USER
                        ).where(
                            WORK_SHIFT_HAS_USER.APP_USER.eq(userId)
                                .and(WORK_SHIFT_HAS_USER.WORK_SHIFT.eq(this@with.ID))
                        )
                    ),
                    filterTimeRange(timeFrom, timeTo),
                    search.metaSearch(searchFields())
                )
            )
        }
    }

    fun pageByEvent(
        eventId: UUID,
        params: PaginationParameters<WorkShiftWithAssignedUsersSort>,
        timeFrom: LocalDateTime?,
        timeTo: LocalDateTime?,
    ): JIO<List<WorkShiftWithAssignedUsersRecord>> = Jooq.query {
        with(WORK_SHIFT_WITH_ASSIGNED_USERS) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId)
                        .and(filterTimeRange(timeFrom, timeTo))
                }
                .fetch()
        }
    }

    fun pageByUser(
        userId: UUID,
        params: PaginationParameters<WorkShiftWithAssignedUsersSort>,
        timeFrom: LocalDateTime?,
        timeTo: LocalDateTime?,
    ): JIO<List<WorkShiftWithAssignedUsersRecord>> = Jooq.query {
        with(WORK_SHIFT_WITH_ASSIGNED_USERS) {
            selectFrom(this)
                .page(params, searchFields()) {
                    filterTimeRange(timeFrom, timeTo).and(
                        DSL.exists(
                            selectFrom(
                                WORK_SHIFT_HAS_USER
                            ).where(
                                WORK_SHIFT_HAS_USER.APP_USER.eq(userId)
                                    .and(WORK_SHIFT_HAS_USER.WORK_SHIFT.eq(this@with.ID))
                            )
                        )
                    )
                }
                .fetch()
        }
    }

    private fun filterTimeRange(
        timeFrom: LocalDateTime?,
        timeTo: LocalDateTime?,
    ): Condition = DSL.and(
        timeFrom?.let { WORK_SHIFT_WITH_ASSIGNED_USERS.TIME_TO.greaterThan(it) } ?: DSL.trueCondition(),
        timeTo?.let { WORK_SHIFT_WITH_ASSIGNED_USERS.TIME_FROM.lessThan(it) } ?: DSL.trueCondition(),
    )


}