package de.lambda9.ready2race.backend.app.club.control

import de.lambda9.ready2race.backend.app.club.entity.ClubSort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.Club
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object ClubRepo {

    private fun Club.searchFields() = listOf(NAME)

    fun create(
        record: ClubRecord,
    ): JIO<UUID> = CLUB.insertReturning(record) { ID }

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
    ): JIO<ClubRecord?> = CLUB.update(f) { ID.eq(id) }

    fun delete(
        id: UUID
    ): JIO<Int> = CLUB.delete { ID.eq(id) }

}