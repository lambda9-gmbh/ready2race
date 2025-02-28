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

    fun all(): JIO<List<NamedParticipantRecord>> = Jooq.query {
        with(NAMED_PARTICIPANT) {
            selectFrom(this)
                .fetch()
        }
    }

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

    fun findUnknown(
        namedParticipants: List<UUID>
    ): JIO<List<UUID>> = Jooq.query {
        val found = with(NAMED_PARTICIPANT) {
            select(ID)
                .from(this)
                .where(DSL.or(namedParticipants.map { ID.eq(it) }))
                .fetch { it.value1() }
        }
        namedParticipants.filter { !found.contains(it) }
    }
}