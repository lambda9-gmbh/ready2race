package de.lambda9.ready2race.backend.app.club.control

import de.lambda9.ready2race.backend.app.club.entity.ClubSort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.Club
import de.lambda9.ready2race.backend.database.generated.tables.records.BankAccountRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.ROLE
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object ClubRepo {

    private fun Club.searchFields() = listOf(NAME)

    fun any() = CLUB.exists { DSL.trueCondition() }

    fun allAsJson() = CLUB.selectAsJson()

    fun create(
        record: ClubRecord,
    ): JIO<UUID> = CLUB.insertReturning(record) { ID }

    fun create(records: List<ClubRecord>) = CLUB.insert(records)

    fun count(
        search: String?,
        eventId: UUID?
    ): JIO<Int> = Jooq.query {
        with(CLUB) {
            fetchCount(
                this, search.metaSearch(searchFields()).and(
                    eventId?.let {
                        DSL.exists(
                            selectFrom(EVENT_REGISTRATION).where(
                                EVENT_REGISTRATION.CLUB.eq(this.ID).and(EVENT_REGISTRATION.EVENT.eq(it))
                            )
                        )
                    } ?: DSL.trueCondition()
                ))
        }
    }

    fun page(
        params: PaginationParameters<ClubSort>,
        eventId: UUID?
    ): JIO<List<ClubRecord>> = Jooq.query {
        with(CLUB) {
            selectFrom(this)
                .page(params, searchFields()) {
                    eventId?.let {
                        DSL.exists(
                            selectFrom(EVENT_REGISTRATION).where(
                                EVENT_REGISTRATION.CLUB.eq(this.ID).and(EVENT_REGISTRATION.EVENT.eq(it))
                            )
                        )
                    } ?: DSL.trueCondition()
                }
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

    fun getName(
        id: UUID,
    ): JIO<String?> = Jooq.query {
        with(CLUB) {
            select(
                NAME
            )
                .from(this)
                .where(ID.eq(id))
                .fetchOne()
                ?.value1()
        }
    }

    fun update(
        id: UUID,
        f: ClubRecord.() -> Unit
    ): JIO<ClubRecord?> = CLUB.update(f) { ID.eq(id) }

    fun updateUserReferences(records: List<ClubRecord>, userReferences: Map<UUID, Pair<UUID?, UUID?>>) = Jooq.query {
        records.forEach { clubRecord ->
            clubRecord.apply {
                clubRecord.createdBy = userReferences[clubRecord.id]?.first
                clubRecord.updatedBy = userReferences[clubRecord.id]?.second
            }
        }
        batchUpdate(records)
            .execute()
    }

    fun delete(
        id: UUID
    ): JIO<Int> = CLUB.delete { ID.eq(id) }

    fun getOverlapIds(ids: List<UUID>) = CLUB.select({ ID }) { ID.`in`(ids) }

    fun parseJsonToRecord(data: String): JIO<List<ClubRecord>> = Jooq.query {
        fetchFromJSON(data)
            .into(ClubRecord::class.java)
    }

}