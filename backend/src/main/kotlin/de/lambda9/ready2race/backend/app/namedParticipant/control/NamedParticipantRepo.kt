package de.lambda9.ready2race.backend.app.namedParticipant.control

import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantSort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.NamedParticipant
import de.lambda9.ready2race.backend.database.generated.tables.records.NamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object NamedParticipantRepo {

    private fun NamedParticipant.searchFields() = listOf(NAME)

    fun create(record: NamedParticipantRecord) = NAMED_PARTICIPANT.insertReturning(record) { ID }

    fun update(id: UUID, f: NamedParticipantRecord.() -> Unit) = NAMED_PARTICIPANT.update(f) { ID.eq(id) }

    fun delete(id: UUID) = NAMED_PARTICIPANT.delete { ID.eq(id) }

    fun all() = NAMED_PARTICIPANT.select()

    fun getIfExist(
        ids: List<UUID>,
    ): JIO<List<NamedParticipantRecord>> = Jooq.query {
        with(NAMED_PARTICIPANT) {
            selectFrom(this)
                .where(DSL.or(ids.map { ID.eq(it) }))
                .fetch()
        }
    }

    fun count(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(NAMED_PARTICIPANT) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun page(
        params: PaginationParameters<NamedParticipantSort>
    ): JIO<List<NamedParticipantRecord>> = Jooq.query {
        with(NAMED_PARTICIPANT) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun get(id: UUID) = NAMED_PARTICIPANT.selectOne { ID.eq(id) }

    fun findAll(): JIO<List<NamedParticipantRecord>> = Jooq.query {
        selectFrom(NAMED_PARTICIPANT)
            .orderBy(NAMED_PARTICIPANT.NAME)
            .fetch()
    }

    fun getOverlapIds(ids: List<UUID>) = NAMED_PARTICIPANT.select({ ID }) { ID.`in`(ids) }

    fun create(records: List<NamedParticipantRecord>) = NAMED_PARTICIPANT.insert(records)

    fun allAsJson() = NAMED_PARTICIPANT.selectAsJson()

    fun insertJsonData(data: String) = NAMED_PARTICIPANT.insertJsonData(data)
}