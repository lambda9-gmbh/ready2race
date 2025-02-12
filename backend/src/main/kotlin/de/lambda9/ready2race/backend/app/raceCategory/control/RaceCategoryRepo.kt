package de.lambda9.ready2race.backend.app.raceCategory.control

import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategorySort
import de.lambda9.ready2race.backend.database.generated.tables.RaceCategory
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceCategoryRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_CATEGORY
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID


object RaceCategoryRepo {

    private fun RaceCategory.searchFields() = listOf(NAME)

    fun create(
        record: RaceCategoryRecord,
    ): JIO<UUID> = Jooq.query {
        with(RACE_CATEGORY){
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
        with(RACE_CATEGORY) {
            fetchExists(this, ID.eq(id))
        }
    }

    fun count(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(RACE_CATEGORY) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun page(
        params: PaginationParameters<RaceCategorySort>
    ): JIO<List<RaceCategoryRecord>> = Jooq.query {
        with(RACE_CATEGORY) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun update(
        raceCategoryId: UUID,
        f: RaceCategoryRecord.() -> Unit
    ): JIO<RaceCategoryRecord?> = Jooq.query {
        with(RACE_CATEGORY) {
            selectFrom(this)
                .where(ID.eq(raceCategoryId))
                .fetchOne()
                ?.apply {
                    f()
                    update()
                }
        }
    }

    fun delete(
        raceCategoryId: UUID
    ): JIO<Int> = Jooq.query {
        with(RACE_CATEGORY){
            deleteFrom(this)
                .where(ID.eq(raceCategoryId))
                .execute()
        }
    }
}