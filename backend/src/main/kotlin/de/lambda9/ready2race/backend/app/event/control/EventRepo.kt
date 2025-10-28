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
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Condition
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.*

object EventRepo {

    private fun EventView.searchFields() =
        listOf(NAME, REGISTRATION_AVAILABLE_FROM, REGISTRATION_AVAILABLE_TO, DESCRIPTION)

    private fun EventPublicView.searchFields() = listOf(NAME, DESCRIPTION)

    fun create(record: EventRecord) = EVENT.insertReturning(record) { ID }

    fun exists(id: UUID) = EVENT.exists { ID.eq(id) }

    fun get(id: UUID) = EVENT.selectOne { ID.eq(id) }

    fun getPublished(id: UUID) = EVENT.selectOne({ PUBLISHED }) { ID.eq(id) }

    fun update(record: EventRecord, f: EventRecord.() -> Unit) = EVENT.update(record, f)

    fun delete(id: UUID) = EVENT.delete { ID.eq(id) }

    fun getEvents(ids: List<UUID>) = EVENT.select { ID.`in`(ids) }

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
    ): JIO<String?> = EVENT.selectOne({ NAME }) { ID.eq(id) }

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

    fun isChallengeEvent(id: UUID) = EVENT.selectOne({ CHALLENGE_EVENT }) { ID.eq(id) }

    private fun filterScopeView(
        scope: Privilege.Scope?,
    ): Condition = if (scope != Privilege.Scope.GLOBAL) EVENT_VIEW.PUBLISHED.eq(true) else DSL.trueCondition()

    fun getAsJson(eventId: UUID) = EVENT.selectAsJson { ID.eq(eventId) }

    fun insertJsonData(data: String) = EVENT.insertJsonData(data)

    fun getEventsForExport() = EVENT_FOR_EXPORT.select()

    fun getEventsForExportByIds(ids: List<UUID>) = EVENT_FOR_EXPORT.select { ID.`in`(ids) }
}