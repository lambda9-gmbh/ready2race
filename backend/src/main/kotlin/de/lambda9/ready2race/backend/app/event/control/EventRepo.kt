package de.lambda9.ready2race.backend.app.event.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.event.entity.EventPublicViewSort
import de.lambda9.ready2race.backend.app.event.entity.EventSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.Event
import de.lambda9.ready2race.backend.database.generated.tables.EventPublicView
import de.lambda9.ready2race.backend.database.generated.tables.records.EventPublicViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_PUBLIC_VIEW
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.impl.DSL
import java.util.*

object EventRepo {

    private fun Event.searchFields() = listOf(NAME, REGISTRATION_AVAILABLE_FROM, REGISTRATION_AVAILABLE_TO, DESCRIPTION)
    private fun EventPublicView.searchFields() = listOf(NAME, DESCRIPTION)

    fun create(record: EventRecord) = EVENT.insertReturning(record) { ID }

    fun exists(id: UUID) = EVENT.exists { ID.eq(id) }

    fun update(id: UUID, f: EventRecord.() -> Unit) = EVENT.update(f) { ID.eq(id) }

    fun delete(id: UUID) = EVENT.delete { ID.eq(id) }

    fun count(
        search: String?,
        scope: Privilege.Scope?,
    ): JIO<Int> = Jooq.query {
        with(EVENT) {
            fetchCount(
                this,
                DSL.and(
                    filterScope(scope),
                    search.metaSearch(searchFields())
                )

            )
        }
    }

    fun page(
        params: PaginationParameters<EventSort>,
        scope: Privilege.Scope?,
    ): JIO<List<EventRecord>> = Jooq.query {
        with(EVENT) {
            selectFrom(this)
                .page(params, searchFields()) {
                    filterScope(scope)
                }
                .fetch()
        }
    }

    fun countForPublicView(
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(EVENT_PUBLIC_VIEW) {
            fetchCount(
                this,
                search.metaSearch(searchFields())
            )
        }
    }

    fun pageForPublicView(
        params: PaginationParameters<EventPublicViewSort>
    ): JIO<List<EventPublicViewRecord>> = Jooq.query {
        with(EVENT_PUBLIC_VIEW) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun getEvent(
        id: UUID,
        scope: Privilege.Scope?,
    ): JIO<EventRecord?> = Jooq.query {
        with(EVENT) {
            selectFrom(this)
                .where(ID.eq(id)).and(filterScope(scope))
                .fetchOne()
        }
    }

    fun getName(
        id: UUID,
    ): JIO<String?> = Jooq.query {
        with(EVENT) {
            select(
                NAME
            )
                .from(this)
                .where(ID.eq(id))
                .fetchOne()
                ?.value1()
        }
    }

    private fun filterScope(
        scope: Privilege.Scope?,
    ): Condition = if (scope != Privilege.Scope.GLOBAL) EVENT.PUBLISHED.eq(true) else DSL.trueCondition()
}