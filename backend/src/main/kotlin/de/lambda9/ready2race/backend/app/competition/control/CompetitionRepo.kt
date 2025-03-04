package de.lambda9.ready2race.backend.app.competition.control

import de.lambda9.ready2race.backend.app.competition.entity.CompetitionWithPropertiesSort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.CompetitionView
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY_HAS_COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_VIEW
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object CompetitionRepo {

    private fun CompetitionView.searchFields() =
        listOf(ID, EVENT, NAME, SHORT_NAME, IDENTIFIER, CATEGORY_NAME)

    fun create(record: CompetitionRecord) = COMPETITION.insertReturning(record) { ID }

    fun exists(id: UUID) = COMPETITION.exists { ID.eq(id) }

    fun update(id: UUID, f: CompetitionRecord.() -> Unit) = COMPETITION.update(f) { ID.eq(id) }

    fun delete(id: UUID) = COMPETITION.delete { ID.eq(id) }

    fun countWithPropertiesByEvent(
        eventId: UUID,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(COMPETITION_VIEW) {
            fetchCount(this, DSL.and(EVENT.eq(eventId), search.metaSearch(searchFields())))
        }
    }

    fun countWithPropertiesByEventAndEventDay(
        eventId: UUID,
        eventDayId: UUID,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(COMPETITION_VIEW) {
            fetchCount(
                this, DSL.and(
                    EVENT.eq(eventId).and(
                        ID.`in`(
                            select(EVENT_DAY_HAS_COMPETITION.COMPETITION)
                                .from(EVENT_DAY_HAS_COMPETITION)
                                .where(EVENT_DAY_HAS_COMPETITION.EVENT_DAY.eq(eventDayId))
                        )
                    ), search.metaSearch(searchFields())
                )
            )
        }
    }

    fun pageWithPropertiesByEvent(
        eventId: UUID,
        params: PaginationParameters<CompetitionWithPropertiesSort>
    ): JIO<List<CompetitionViewRecord>> = Jooq.query {
        with(COMPETITION_VIEW) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId)
                }
                .fetch()
        }
    }

    fun pageWithPropertiesByEventAndEventDay(
        eventId: UUID,
        eventDayId: UUID,
        params: PaginationParameters<CompetitionWithPropertiesSort>
    ): JIO<List<CompetitionViewRecord>> = Jooq.query {
        with(COMPETITION_VIEW) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId)
                        .and(
                            ID.`in`(
                                select(EVENT_DAY_HAS_COMPETITION.COMPETITION)
                                    .from(EVENT_DAY_HAS_COMPETITION)
                                    .where(EVENT_DAY_HAS_COMPETITION.EVENT_DAY.eq(eventDayId))
                            )
                        )
                }
                .fetch()
        }
    }

    fun getWithProperties(
        competitionId: UUID
    ): JIO<CompetitionViewRecord?> = Jooq.query {
        with(COMPETITION_VIEW) {
            selectFrom(this)
                .where(ID.eq(competitionId))
                .fetchOne()
        }
    }

    fun findUnknown(
        competitions: List<UUID>
    ): JIO<List<UUID>> = Jooq.query {
        val found = with(COMPETITION) {
            select(ID)
                .from(this)
                .where(DSL.or(competitions.map { ID.eq(it) }))
                .fetch { it.value1() }
        }
        competitions.filter { !found.contains(it) }
    }
}