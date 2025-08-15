package de.lambda9.ready2race.backend.app.task.control

import de.lambda9.ready2race.backend.app.task.entity.TaskWithResponsibleUsersSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.generated.enums.TaskState
import de.lambda9.ready2race.backend.database.generated.tables.TaskWithResponsibleUsers
import de.lambda9.ready2race.backend.database.generated.tables.records.TaskWithResponsibleUsersRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.TASK_HAS_RESPONSIBLE_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.TASK_WITH_RESPONSIBLE_USERS
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object TaskWithResponsibleUsersRepo {

    private fun TaskWithResponsibleUsers.searchFields() = listOf(NAME)

    fun countByEvent(
        eventId: UUID,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(TASK_WITH_RESPONSIBLE_USERS) {
            fetchCount(
                this,
                DSL.and(
                    EVENT.eq(eventId),
                    search.metaSearch(searchFields())
                )
            )
        }
    }

    fun pageByEvent(
        eventId: UUID,
        params: PaginationParameters<TaskWithResponsibleUsersSort>,
    ): JIO<List<TaskWithResponsibleUsersRecord>> = Jooq.query {
        with(TASK_WITH_RESPONSIBLE_USERS) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId)
                }
                .fetch()
        }
    }

    fun countByUser(
        userId: UUID,
        states: List<TaskState>,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(TASK_WITH_RESPONSIBLE_USERS) {
            fetchCount(
                this
                    .join(TASK_HAS_RESPONSIBLE_USER).on(this.ID.eq(TASK_HAS_RESPONSIBLE_USER.TASK)),
                DSL.and(
                    STATE.`in`(states),
                    TASK_HAS_RESPONSIBLE_USER.APP_USER.eq(userId),
                    search.metaSearch(searchFields())
                )
            )
        }
    }

    fun pageByUser(
        userId: UUID,
        states: List<TaskState>,
        params: PaginationParameters<TaskWithResponsibleUsersSort>,
    ): JIO<List<TaskWithResponsibleUsersRecord>> = Jooq.query {
        with(TASK_WITH_RESPONSIBLE_USERS) {
            select(TASK_WITH_RESPONSIBLE_USERS.asterisk())
                .from(this)
                .join(TASK_HAS_RESPONSIBLE_USER).on(this.ID.eq(TASK_HAS_RESPONSIBLE_USER.TASK))
                .page(params, searchFields()) {
                    STATE.`in`(states).and(
                        TASK_HAS_RESPONSIBLE_USER.APP_USER.eq(userId)
                    )
                }
                .fetchInto(TaskWithResponsibleUsersRecord::class.java)
        }
    }

}