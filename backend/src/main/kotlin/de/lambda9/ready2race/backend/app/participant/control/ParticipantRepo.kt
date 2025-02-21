package de.lambda9.ready2race.backend.app.participant.control

import de.lambda9.ready2race.backend.app.participant.entity.ParticipantSort
import de.lambda9.ready2race.backend.database.generated.tables.Participant
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object ParticipantRepo {

    private fun Participant.searchFields() = listOf(FIRSTNAME, LASTNAME, EXTERNAL_CLUB_NAME)

    fun create(
        record: ParticipantRecord,
    ): JIO<UUID> = Jooq.query {
        with(PARTICIPANT) {
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }

    fun count(
        search: String?,
        clubId: UUID?,
    ): JIO<Int> = Jooq.query {
        with(PARTICIPANT) {
            fetchCount(
                this, search.metaSearch(searchFields())
                    .and(
                        clubId?.let { PARTICIPANT.CLUB.eq(it) } ?: DSL.trueCondition()
                    )
            )
        }
    }

    fun page(
        params: PaginationParameters<ParticipantSort>,
        clubId: UUID?,
    ): JIO<List<ParticipantRecord>> = Jooq.query {
        with(PARTICIPANT) {
            selectFrom(this)
                .page(params, searchFields()) {
                    clubId?.let { PARTICIPANT.CLUB.eq(it) } ?: DSL.trueCondition()
                }
                .fetch()
        }
    }

    fun getParticipant(
        id: UUID,
        clubId: UUID?,
    ): JIO<ParticipantRecord?> = Jooq.query {
        with(PARTICIPANT) {
            selectFrom(this)
                .where(ID.eq(id))
                .and(
                    clubId?.let { PARTICIPANT.CLUB.eq(it) } ?: DSL.trueCondition()
                )
                .fetchOne()
        }
    }

    fun update(
        id: UUID,
        clubId: UUID?,
        f: ParticipantRecord.() -> Unit
    ): JIO<ParticipantRecord?> = Jooq.query {
        with(PARTICIPANT) {
            selectFrom(this)
                .where(ID.eq(id))
                .and(
                    clubId?.let { PARTICIPANT.CLUB.eq(it) } ?: DSL.trueCondition()
                )
                .fetchOne()
                ?.apply {
                    f()
                    update()
                }
        }
    }

    fun delete(
        id: UUID,
        clubId: UUID?,
    ): JIO<Int> = Jooq.query {
        with(PARTICIPANT) {
            deleteFrom(this)
                .where(ID.eq(id))
                .and(
                    clubId?.let { PARTICIPANT.CLUB.eq(it) } ?: DSL.trueCondition()
                )
                .execute()
        }
    }

}