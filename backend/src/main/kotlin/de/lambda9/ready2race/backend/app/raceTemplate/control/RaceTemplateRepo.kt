package de.lambda9.ready2race.backend.app.raceTemplate.control

import de.lambda9.ready2race.backend.app.raceTemplate.entity.RaceTemplateWithPropertiesSort
import de.lambda9.ready2race.backend.database.generated.tables.RaceTemplateToPropertiesWithNamedParticipants
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceTemplateToPropertiesWithNamedParticipantsRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object RaceTemplateRepo {

    private fun RaceTemplateToPropertiesWithNamedParticipants.searchFields() =
        listOf(ID, NAME, SHORT_NAME, IDENTIFIER, CATEGORY_NAME)

    fun create(
        record: RaceTemplateRecord,
    ): JIO<UUID> = Jooq.query {
        with(RACE_TEMPLATE) {
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
        with(RACE_TEMPLATE) {
            fetchExists(this, ID.eq(id))
        }
    }

    fun countWithProperties(
        search: String?
    ): JIO<Int> = Jooq.query {
        with(RACE_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun pageWithProperties(
        params: PaginationParameters<RaceTemplateWithPropertiesSort>
    ): JIO<List<RaceTemplateToPropertiesWithNamedParticipantsRecord>> = Jooq.query {
        with(RACE_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }

    fun getWithProperties(
        templateId: UUID
    ): JIO<RaceTemplateToPropertiesWithNamedParticipantsRecord?> = Jooq.query {
        with(RACE_TEMPLATE_TO_PROPERTIES_WITH_NAMED_PARTICIPANTS) {
            selectFrom(this)
                .where(ID.eq(templateId))
                .fetchOne()
        }
    }


    fun update(
        id: UUID,
        f: RaceTemplateRecord.() -> Unit
    ): JIO<Boolean> = Jooq.query {
        with(RACE_TEMPLATE) {
            (selectFrom(this)
                .where(ID.eq(id))
                .fetchOne() ?: return@query false)
                .apply(f)
                .update()
        }
        true
    }

    fun delete(
        id: UUID
    ): JIO<Int> = Jooq.query {
        with(RACE_TEMPLATE) {
            deleteFrom(this)
                .where(ID.eq(id))
                .execute()
        }
    }
}