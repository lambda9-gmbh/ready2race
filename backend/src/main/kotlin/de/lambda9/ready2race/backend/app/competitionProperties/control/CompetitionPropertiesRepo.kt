package de.lambda9.ready2race.backend.app.competitionProperties.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object CompetitionPropertiesRepo {

    fun create(
        record: CompetitionPropertiesRecord,
    ): JIO<UUID> = Jooq.query {
        with(COMPETITION_PROPERTIES) {
            insertInto(this)
                .set(record)
                .returningResult(ID)
                .fetchOne()!!
                .value1()!!
        }
    }

    // todo: use properties id instead
    fun updateByCompetitionOrTemplate(
        id: UUID,
        f: CompetitionPropertiesRecord.() -> Unit
    ): JIO<CompetitionPropertiesRecord?> = Jooq.query {
        with(COMPETITION_PROPERTIES) {
            selectFrom(this)
                .where(COMPETITION.eq(id).or(COMPETITION_TEMPLATE.eq(id)))
                .fetchOne()
                ?.apply {
                    f()
                    update()
                }
        }
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

}