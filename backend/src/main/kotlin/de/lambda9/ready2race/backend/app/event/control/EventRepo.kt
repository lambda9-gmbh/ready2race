package de.lambda9.ready2race.backend.app.event.control

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.event.entity.EventPublicViewSort
import de.lambda9.ready2race.backend.app.event.entity.EventViewSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.EventPublicView
import de.lambda9.ready2race.backend.database.generated.tables.EventView
import de.lambda9.ready2race.backend.database.generated.tables.records.EventPublicViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_PUBLIC_VIEW
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_VIEW
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.*

object EventRepo {

    private fun EventView.searchFields() = listOf(NAME, REGISTRATION_AVAILABLE_FROM, REGISTRATION_AVAILABLE_TO, DESCRIPTION)
    private fun EventPublicView.searchFields() = listOf(NAME, DESCRIPTION)

    fun create(record: EventRecord) = EVENT.insertReturning(record) { ID }

    fun exists(id: UUID) = EVENT.exists { ID.eq(id) }

    fun get(id: UUID) = EVENT.selectOne { ID.eq(id) }

    fun update(id: UUID, f: EventRecord.() -> Unit) = EVENT.update(f) { ID.eq(id) }

    fun delete(id: UUID) = EVENT.delete { ID.eq(id) }

    fun count(
        search: String?,
        scope: Privilege.Scope?,
    ): JIO<Int> = Jooq.query {
        with(EVENT_VIEW) {
            fetchCount(
                this,
                DSL.and(
                    filterScopeView(scope),
                    search.metaSearch(searchFields())
                )

            )
        }
    }

    fun page(
        params: PaginationParameters<EventViewSort>,
        scope: Privilege.Scope?,
    ): JIO<List<EventViewRecord>> = Jooq.query {
        with(EVENT_VIEW) {
            selectFrom(this)
                .page(params, searchFields()) {
                    filterScopeView(scope)
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

    fun getScoped(
        id: UUID,
        scope: Privilege.Scope?,
    ): JIO<EventViewRecord?> = Jooq.query {
        with(EVENT_VIEW) {
            selectFrom(this)
                .where(ID.eq(id)).and(filterScopeView(scope))
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

    fun isOpenForRegistration(id: UUID, at: LocalDateTime): JIO<Boolean> = Jooq.query {
        with(EVENT) {
            fetchExists(
                this.where(
                    ID.eq(id)
                        .and(REGISTRATION_AVAILABLE_FROM.le(at))
                        .and(
                            DSL.or(
                                REGISTRATION_AVAILABLE_TO.isNull,
                                REGISTRATION_AVAILABLE_TO.ge(at)
                            )
                        )
                        .and(PUBLISHED.isTrue)
                )
            )
        }
    }

    fun isOpenForLateRegistration(id: UUID, at: LocalDateTime) = EVENT.exists {
        DSL.and(
            ID.eq(id),
            REGISTRATION_AVAILABLE_TO.le(at),
            LATE_REGISTRATION_AVAILABLE_TO.ge(at),
            PUBLISHED.isTrue
        )
    }

    private fun filterScopeView(
        scope: Privilege.Scope?,
    ): Condition = if (scope != Privilege.Scope.GLOBAL) EVENT_VIEW.PUBLISHED.eq(true) else DSL.trueCondition()
}