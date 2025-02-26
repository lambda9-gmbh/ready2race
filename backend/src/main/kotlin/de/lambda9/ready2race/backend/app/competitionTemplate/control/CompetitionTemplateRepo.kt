package de.lambda9.ready2race.backend.app.competitionTemplate.control

import de.lambda9.ready2race.backend.app.competitionTemplate.entity.CompetitionTemplateWithPropertiesSort
import de.lambda9.ready2race.backend.database.generated.tables.CompetitionTemplateToPropertiesWithNamedParticipants
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionTemplateToPropertiesWithNamedParticipantsRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object CompetitionTemplateRepo {

    private fun CompetitionTemplateToPropertiesWithNamedParticipants.searchFields() =
        listOf(ID, NAME, SHORT_NAME, IDENTIFIER, CATEGORY_NAME)

    fun create(
        record: CompetitionTemplateRecord,
    ): JIO<UUID> = Jooq.query {
        with(COMPETITION_TEMPLATE) {
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }

    fun exists(
        id: UUID,
    ): JIO<Boolean> = Jooq.query {
        with(COMPETITION_TEMPLATE) {
            fetchExists(this, ID.eq(id))
        }
    }

    fun countWithProperties(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(COMPETITION_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun pageWithProperties(
        params: PaginationParameters<CompetitionTemplateWithPropertiesSort>
    ): JIO<List<CompetitionTemplateToPropertiesWithNamedParticipantsRecord>> = Jooq.query {
        with(COMPETITION_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun getWithProperties(
        templateId: UUID
    ): JIO<CompetitionTemplateToPropertiesWithNamedParticipantsRecord?> = Jooq.query {
        with(COMPETITION_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS) {
            selectFrom(this)
                .where(ID.eq(templateId))
                .fetchOne()
        }
    }


    fun update(
        id: UUID,
        f: CompetitionTemplateRecord.() -> Unit
    ): JIO<CompetitionTemplateRecord?> = Jooq.query {
        with(COMPETITION_TEMPLATE) {
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
        with(COMPETITION_TEMPLATE) {
            deleteFrom(this)
                .where(ID.eq(id))
                .execute()
        }
    }
}