package de.lambda9.ready2race.backend.app.race.control

import de.lambda9.ready2race.backend.app.race.entity.RaceWithPropertiesSort
import de.lambda9.ready2race.backend.database.generated.tables.RaceWithProperties
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceWithPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.RACE
import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_WITH_PROPERTIES
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.http.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object RaceRepo {

    private fun RaceWithProperties.searchFields() = listOf(ID, EVENT, NAME, SHORT_NAME, IDENTIFIER, RACE_CATEGORY)

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

    fun countWithPropertiesByEvent(
        eventId: UUID,
        search: String?
    ): JIO<Int> = Jooq.query {
        with(RACE_WITH_PROPERTIES) {
            fetchCount(this, DSL.and(RACE_WITH_PROPERTIES.EVENT.eq(eventId), search.metaSearch(searchFields())))
        }
    }

    fun pageWithPropertiesByEvent(
        eventId: UUID,
        params: PaginationParameters<RaceWithPropertiesSort>
    ): JIO<List<RaceWithPropertiesRecord>> = Jooq.query {
        with(RACE_WITH_PROPERTIES) {
            selectFrom(this)
                .page(params, searchFields()){
                    EVENT.eq(eventId)
                }
                .fetch()
        }
    }

    fun getWithProperties(
        raceId: UUID
    ): JIO<RaceWithPropertiesRecord?> = Jooq.query {
        with(RACE_WITH_PROPERTIES) {
            selectFrom(this)
                .where(ID.eq(raceId))
                .fetchOne()
        }
    }

    fun getWith

    fun update(
        id: UUID,
        f: RaceRecord.() -> Unit
    ): JIO<Unit> = Jooq.query {
        with(RACE){
            selectFrom(this)
                .where(ID.eq(id))
                .fetchOne()
                ?.apply(f)
                ?.update()
        }
    }

    fun delete(
        id: UUID
    ): JIO<Int> = Jooq.query {
        with(RACE){
            deleteFrom(this)
                .where(ID.eq(id))
                .execute()
        }
    }
}