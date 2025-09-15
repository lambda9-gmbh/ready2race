package de.lambda9.ready2race.backend.app.competitionSetupTemplate.control

import de.lambda9.ready2race.backend.app.competitionSetupTemplate.entity.CompetitionSetupTemplateSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.CompetitionSetupTemplate
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_TEMPLATE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object CompetitionSetupTemplateRepo {

    private fun CompetitionSetupTemplate.searchFields() =
        listOf(NAME, DESCRIPTION)

    fun create(record: CompetitionSetupTemplateRecord) = COMPETITION_SETUP_TEMPLATE.insertReturning(record) { ID }

    fun update(id: UUID, f: CompetitionSetupTemplateRecord.() -> Unit) =
        COMPETITION_SETUP_TEMPLATE.update(f) { ID.eq(id) }

    fun delete(id: UUID) = COMPETITION_SETUP_TEMPLATE.delete { ID.eq(id) }

    fun exists(id: UUID) = COMPETITION_SETUP_TEMPLATE.exists { ID.eq(id) }

    fun count(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(COMPETITION_SETUP_TEMPLATE) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun page(
        params: PaginationParameters<CompetitionSetupTemplateSort>
    ): JIO<List<CompetitionSetupTemplateRecord>> = Jooq.query {
        with(COMPETITION_SETUP_TEMPLATE) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun get(
        id: UUID
    ): JIO<CompetitionSetupTemplateRecord?> = Jooq.query {
        with(COMPETITION_SETUP_TEMPLATE) {
            selectFrom(this)
                .where(ID.eq(id))
                .fetchOne()
        }
    }

    fun get(): JIO<List<CompetitionSetupTemplateRecord>> = Jooq.query {
        with(COMPETITION_SETUP_TEMPLATE) {
            selectFrom(this)
                .fetch()
        }
    }

    fun all() = COMPETITION_SETUP_TEMPLATE.select()

    fun create(records: Collection<CompetitionSetupTemplateRecord>) = COMPETITION_SETUP_TEMPLATE.insert(records)

    fun getOverlapIds(ids: List<UUID>) = COMPETITION_SETUP_TEMPLATE.select({ ID }) { ID.`in`(ids) }
}