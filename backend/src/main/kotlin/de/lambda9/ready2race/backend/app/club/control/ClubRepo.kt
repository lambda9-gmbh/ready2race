package de.lambda9.ready2race.backend.app.club.control

import de.lambda9.ready2race.backend.app.club.entity.ClubSort
import de.lambda9.ready2race.backend.database.generated.tables.Club
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object ClubRepo {

    private fun Club.searchFields() = listOf(NAME)

    fun create(
        record: ClubRecord,
    ): JIO<UUID> = Jooq.query {
        with(CLUB) {
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }

    fun count(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(CLUB) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun page(
        params: PaginationParameters<ClubSort>
    ): JIO<List<ClubRecord>> = Jooq.query {
        with(CLUB) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun getClub(
        id: UUID
    ): JIO<ClubRecord?> = Jooq.query {
        with(CLUB) {
            selectFrom(this)
                .where(ID.eq(id))
                .fetchOne()
        }
    }

    fun update(
        id: UUID,
        f: ClubRecord.() -> Unit
    ): JIO<ClubRecord?> = Jooq.query {
        with(CLUB) {
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
        with(CLUB) {
            deleteFrom(this)
                .where(ID.eq(id))
                .execute()
        }
    }

}