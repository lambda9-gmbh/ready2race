package de.lambda9.ready2race.backend.app.competitionProperties.control

import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesContainingReference
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesHasNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.select
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object CompetitionPropertiesHasNamedParticipantRepo {

    fun create(records: Collection<CompetitionPropertiesHasNamedParticipantRecord>) =
        COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT.insert(records)

    fun deleteByCompetitionPropertiesId(competitionPropertiesId: UUID) =
        COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT.delete { COMPETITION_PROPERTIES.eq(competitionPropertiesId) }

    fun getByCompetitionAndNamedParticipantId(competitionId: UUID, namedParticipantId: UUID) = Jooq.query {
        select(COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT.asterisk())
            .from(COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT)
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_PROPERTIES.ID.eq(COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT.COMPETITION_PROPERTIES))
            .where(
                COMPETITION_PROPERTIES.COMPETITION.eq(competitionId)
                    .and(COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT.NAMED_PARTICIPANT.eq(namedParticipantId))
            )
            .fetchOneInto(CompetitionPropertiesHasNamedParticipantRecord::class.java)
    }

    // todo @style refactor?
    fun getByNamedParticipant(
        namedParticipant: UUID
    ): JIO<List<CompetitionPropertiesContainingReference>> = Jooq.query {
        select(
            COMPETITION_PROPERTIES.COMPETITION_TEMPLATE,
            COMPETITION_PROPERTIES.COMPETITION,
            COMPETITION_PROPERTIES.NAME,
            COMPETITION_PROPERTIES.SHORT_NAME
        ).from(COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT)
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT.COMPETITION_PROPERTIES.eq(COMPETITION_PROPERTIES.ID))
            .where(COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT.NAMED_PARTICIPANT.eq(namedParticipant))
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

    fun getByProperties(ids: List<UUID>) =
        COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT.select { COMPETITION_PROPERTIES.`in`(ids) }

    fun getOverlaps(keys: List<Pair<UUID, UUID>>) = COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT.select {
        DSL.or(keys.map { (properties, namedParticipant) ->
            COMPETITION_PROPERTIES.eq(properties).and(NAMED_PARTICIPANT.eq(namedParticipant))
        })
    }
}
