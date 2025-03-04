package de.lambda9.ready2race.backend.app.competitionTemplate.control

import de.lambda9.ready2race.backend.app.competitionTemplate.entity.CompetitionTemplateWithPropertiesSort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.CompetitionTemplateView
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionTemplateViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object CompetitionTemplateRepo {

    private fun CompetitionTemplateView.searchFields() =
        listOf(ID, NAME, SHORT_NAME, IDENTIFIER, CATEGORY_NAME)

    fun create(record: CompetitionTemplateRecord) = COMPETITION_TEMPLATE.insertReturning(record) { ID }

    fun exists(id: UUID) = COMPETITION_TEMPLATE.exists { ID.eq(id) }

    fun update(id: UUID, f: CompetitionTemplateRecord.() -> Unit) = COMPETITION_TEMPLATE.update(f) { ID.eq(id) }

    fun delete(id: UUID) = COMPETITION_TEMPLATE.delete { ID.eq(id) }

    fun countWithProperties(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(COMPETITION_TEMPLATE_VIEW) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun pageWithProperties(
        params: PaginationParameters<CompetitionTemplateWithPropertiesSort>
    ): JIO<List<CompetitionTemplateViewRecord>> = Jooq.query {
        with(COMPETITION_TEMPLATE_VIEW) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun getWithProperties(
        templateId: UUID
    ): JIO<CompetitionTemplateViewRecord?> = Jooq.query {
        with(COMPETITION_TEMPLATE_VIEW) {
            selectFrom(this)
                .where(ID.eq(templateId))
                .fetchOne()
        }
    }
}