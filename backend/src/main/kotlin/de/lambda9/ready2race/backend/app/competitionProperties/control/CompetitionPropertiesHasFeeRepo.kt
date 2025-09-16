package de.lambda9.ready2race.backend.app.competitionProperties.control

import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesContainingReference
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesHasFeeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES_HAS_FEE
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_TEMPLATE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.select
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object CompetitionPropertiesHasFeeRepo {

    fun create(records: Collection<CompetitionPropertiesHasFeeRecord>) = COMPETITION_PROPERTIES_HAS_FEE.insert(records)

    fun existsByCompetitionIdAndFeeId(competitionId: UUID, feeId: UUID) = Jooq.query {
        fetchExists(
            COMPETITION_PROPERTIES_HAS_FEE.join(COMPETITION_PROPERTIES)
                .on(COMPETITION_PROPERTIES.ID.eq(COMPETITION_PROPERTIES_HAS_FEE.COMPETITION_PROPERTIES))
                .where(
                    COMPETITION_PROPERTIES_HAS_FEE.FEE.eq(feeId)
                        .and(COMPETITION_PROPERTIES.COMPETITION.eq(competitionId))
                )
        )
    }

    fun deleteManyByCompetitionProperties(
        competitionPropertiesId: UUID,
    ): JIO<Int> = Jooq.query {
        with(COMPETITION_PROPERTIES_HAS_FEE) {
            deleteFrom(this)
                .where(COMPETITION_PROPERTIES.eq(competitionPropertiesId))
                .execute()
        }
    }

    // todo @style refactor?
    fun getByFee(
        feeId: UUID
    ): JIO<List<CompetitionPropertiesContainingReference>> = Jooq.query {
        select(
            COMPETITION_PROPERTIES.COMPETITION_TEMPLATE,
            COMPETITION_PROPERTIES.COMPETITION,
            COMPETITION_PROPERTIES.NAME,
            COMPETITION_PROPERTIES.SHORT_NAME
        ).from(COMPETITION_PROPERTIES_HAS_FEE)
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_PROPERTIES_HAS_FEE.COMPETITION_PROPERTIES.eq(COMPETITION_PROPERTIES.ID))
            .where(COMPETITION_PROPERTIES_HAS_FEE.FEE.eq(feeId))
            .fetch()
            .map {
                CompetitionPropertiesContainingReference(
                    competitionTemplateId = it[COMPETITION_PROPERTIES.COMPETITION_TEMPLATE],
                    competitionId = it[COMPETITION_PROPERTIES.COMPETITION],
                    name = it[COMPETITION_PROPERTIES.NAME]!!,
                    shortName = it[COMPETITION_PROPERTIES.SHORT_NAME]
                )
            }
    }

    fun getByProperties(ids: List<UUID>) = COMPETITION_PROPERTIES_HAS_FEE.select { COMPETITION_PROPERTIES.`in`(ids) }

    fun getOverlapIds(ids: List<UUID>) = COMPETITION_PROPERTIES_HAS_FEE.select({ ID }) { ID.`in`(ids) }

}