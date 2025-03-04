package de.lambda9.ready2race.backend.app.competitionProperties.control

import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesContainingReference
import de.lambda9.ready2race.backend.database.generated.tables.CompetitionCategory
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object CompetitionPropertiesRepo {

    fun create(record: CompetitionPropertiesRecord) = COMPETITION_PROPERTIES.insertReturning(record) { ID }

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
                .map{
                    CompetitionPropertiesContainingReference(
                        competitionTemplateId = it[COMPETITION_TEMPLATE],
                        competitionId = it[COMPETITION],
                        name = it[NAME]!!,
                        shortName = it[SHORT_NAME]
                    )
                }
        }
    }
}