package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.ParticipantRequirement
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object ParticipantRequirementRepo {

    private fun ParticipantRequirement.searchFields() = listOf(NAME)

    fun create(record: ParticipantRequirementRecord) =
        PARTICIPANT_REQUIREMENT.insertReturning(record) { PARTICIPANT_REQUIREMENT.ID }

    fun create(records: List<ParticipantRequirementRecord>) = PARTICIPANT_REQUIREMENT.insert(records)

    fun getOverlapIds(ids: List<UUID>) = PARTICIPANT_REQUIREMENT.select({ ID }) { ID.`in`(ids) }

    fun update(id: UUID, f: ParticipantRequirementRecord.() -> Unit) = PARTICIPANT_REQUIREMENT.update(f) { ID.eq(id) }

    fun delete(
        id: UUID,
    ) = PARTICIPANT_REQUIREMENT.delete {
        ID.eq(id)
    }

    fun count(
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(PARTICIPANT_REQUIREMENT) {
            fetchCount(
                this, search.metaSearch(searchFields())
            )
        }
    }

    fun page(
        params: PaginationParameters<ParticipantRequirementSort>,
    ): JIO<List<ParticipantRequirementRecord>> = Jooq.query {
        with(PARTICIPANT_REQUIREMENT) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun get(id: UUID) = PARTICIPANT_REQUIREMENT.selectOne { ID.eq(id) }

}