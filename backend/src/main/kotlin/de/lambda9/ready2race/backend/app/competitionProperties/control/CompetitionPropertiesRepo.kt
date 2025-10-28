package de.lambda9.ready2race.backend.app.competitionProperties.control

import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesContainingReference
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object CompetitionPropertiesRepo {

    fun create(record: CompetitionPropertiesRecord) = COMPETITION_PROPERTIES.insertReturning(record) { ID }

    fun create(records: List<CompetitionPropertiesRecord>) = COMPETITION_PROPERTIES.insert(records)

    // todo: use properties id instead
    fun updateByCompetitionOrTemplate(id: UUID, f: CompetitionPropertiesRecord.() -> Unit) =
        COMPETITION_PROPERTIES.update(f) {
            DSL.or(
                COMPETITION.eq(id),
                COMPETITION_TEMPLATE.eq(id)
            )
        }

    fun getIdByCompetitionOrTemplateId(
        id: UUID
    ): JIO<UUID?> = Jooq.query {
        with(COMPETITION_PROPERTIES) {
            select(ID)
                .from(this)
                .where(COMPETITION.eq(id).or(COMPETITION_TEMPLATE.eq(id)))
                .fetchOneInto(UUID::class.java)
        }
    }

    // todo @style refactor?
    fun getByCompetitionCategory(
        competitionCategory: UUID
    ): JIO<List<CompetitionPropertiesContainingReference>> = Jooq.query {
        with(COMPETITION_PROPERTIES) {
            select(
                COMPETITION_TEMPLATE,
                COMPETITION,
                NAME,
                SHORT_NAME
            ).from(this)
                .where(COMPETITION_CATEGORY.eq(competitionCategory))
                .fetch()
                .map {
                    CompetitionPropertiesContainingReference(
                        competitionTemplateId = it[COMPETITION_TEMPLATE],
                        competitionId = it[COMPETITION],
                        name = it[NAME]!!,
                        shortName = it[SHORT_NAME]
                    )
                }
        }
    }

    fun getIdsByCompetitionOrTemplateIds(keys: List<UUID>) =
        COMPETITION_PROPERTIES.select({ ID }) { COMPETITION.`in`(keys).or(COMPETITION_TEMPLATE.`in`(keys)) }

    fun getByCompetitionOrTemplateIdsAsJson(keys: List<UUID>) =
        COMPETITION_PROPERTIES.selectAsJson { COMPETITION.`in`(keys).or(COMPETITION_TEMPLATE.`in`(keys)) }

    fun getOverlapIds(ids: List<UUID>) = COMPETITION_PROPERTIES.select({ ID }) { ID.`in`(ids) }

    fun getAsJson(propertiesId: UUID) = COMPETITION_PROPERTIES.selectAsJson { ID.eq(propertiesId) }

    fun insertJsonData(data: String) = COMPETITION_PROPERTIES.insertJsonData(data)


    fun getEventIdByCompetitionPropertiesId(
        competitionPropertiesId: UUID
    ): JIO<UUID?> = Jooq.query {
        select(COMPETITION.EVENT)
            .from(COMPETITION_PROPERTIES)
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION_PROPERTIES.ID.eq(competitionPropertiesId))
            .fetchOne()
            ?.value1()
    }

    fun getRatingCategoryRequired(
        competitionId: UUID
    ): JIO<Boolean?> = COMPETITION_PROPERTIES.selectOne({ RATING_CATEGORY_REQUIRED }) { COMPETITION.eq(competitionId) }
}