package de.lambda9.ready2race.backend.app.race.control

import de.lambda9.ready2race.backend.app.race.entity.RaceWithPropertiesSort
import de.lambda9.ready2race.backend.database.generated.tables.RaceToPropertiesWithNamedParticipants
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceToPropertiesWithNamedParticipantsRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DAY_HAS_RACE
import de.lambda9.ready2race.backend.database.generated.tables.references.RACE
import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object RaceRepo {

    private fun RaceToPropertiesWithNamedParticipants.searchFields() =
        listOf(ID, EVENT, NAME, SHORT_NAME, IDENTIFIER, CATEGORY_NAME)

    fun create(
        record: RaceRecord,
    ): JIO<UUID> = Jooq.query {
        with(RACE) {
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }


    fun exists(
        id: UUID
    ): JIO<Boolean> = Jooq.query {
        with(RACE) {
            fetchExists(this, ID.eq(id))
        }
    }

    fun countWithPropertiesByEvent(
        eventId: UUID,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS) {
            fetchCount(this, DSL.and(EVENT.eq(eventId), search.metaSearch(searchFields())))
        }
    }

    fun countWithPropertiesByEventAndEventDay(
        eventId: UUID,
        eventDayId: UUID,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS) {
            fetchCount(
                this, DSL.and(
                    EVENT.eq(eventId).and(
                        ID.`in`(
                            select(EVENT_DAY_HAS_RACE.RACE)
                                .from(EVENT_DAY_HAS_RACE)
                                .where(EVENT_DAY_HAS_RACE.EVENT_DAY.eq(eventDayId))
                        )
                    ), search.metaSearch(searchFields())
                )
            )
        }
    }

    fun pageWithPropertiesByEvent(
        eventId: UUID,
        params: PaginationParameters<RaceWithPropertiesSort>
    ): JIO<List<RaceToPropertiesWithNamedParticipantsRecord>> = Jooq.query {
        with(RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS) {
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
        params: PaginationParameters<RaceWithPropertiesSort>
    ): JIO<List<RaceToPropertiesWithNamedParticipantsRecord>> = Jooq.query {
        with(RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT.eq(eventId)
                        .and(
                            ID.`in`(
                                select(EVENT_DAY_HAS_RACE.RACE)
                                    .from(EVENT_DAY_HAS_RACE)
                                    .where(EVENT_DAY_HAS_RACE.EVENT_DAY.eq(eventDayId))
                            )
                        )
                }
                .fetch()
        }
    }

    fun getWithProperties(
        raceId: UUID
    ): JIO<RaceToPropertiesWithNamedParticipantsRecord?> = Jooq.query {
        with(RACE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS) {
            selectFrom(this)
                .where(ID.eq(raceId))
                .fetchOne()
        }
    }

    fun update(
        id: UUID,
        f: RaceRecord.() -> Unit
    ): JIO<RaceRecord?> = Jooq.query {
        with(RACE) {
            selectFrom(this)
                .where(ID.eq(id))
                .fetchOne()
                ?.apply {
                    f()
                    update()
                }
        }
    }

    fun delete(
        id: UUID
    ): JIO<Int> = Jooq.query {
        with(RACE) {
            deleteFrom(this)
                .where(ID.eq(id))
                .execute()
        }
    }

    fun findUnknown(
        races: List<UUID>
    ): JIO<List<UUID>> = Jooq.query {
        val found = with(RACE) {
            select(ID)
                .from(this)
                .where(DSL.or(races.map { ID.eq(it) }))
                .fetch { it.value1() }
        }
        races.filter { !found.contains(it) }
    }
}