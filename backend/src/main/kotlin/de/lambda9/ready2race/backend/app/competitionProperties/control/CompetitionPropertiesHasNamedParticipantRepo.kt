package de.lambda9.ready2race.backend.app.competitionProperties.control

import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesContainingNamedParticipant
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesHasNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object CompetitionPropertiesHasNamedParticipantRepo {

    fun create(records: Collection<CompetitionPropertiesHasNamedParticipantRecord>) = COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT.insert(records)

    fun deleteManyByCompetitionProperties(
        competitionPropertiesId: UUID,
    ): JIO<Int> = Jooq.query {
        with(COMPETITION_PROPERTIES_HAS_NAMED_PARTICIPANT) {
            deleteFrom(this)
                .where(COMPETITION_PROPERTIES.eq(competitionPropertiesId))
                .execute()
        }
    }

    fun getByNamedParticipant(
        namedParticipant: UUID
    ): JIO<List<CompetitionPropertiesContainingNamedParticipant>> = Jooq.query {
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
                CompetitionPropertiesContainingNamedParticipant(
                    competitionTemplateId = it[COMPETITION_PROPERTIES.COMPETITION_TEMPLATE],
                    competitionId = it[COMPETITION_PROPERTIES.COMPETITION],
                    name = it[COMPETITION_PROPERTIES.NAME]!!,
                    shortName = it[COMPETITION_PROPERTIES.SHORT_NAME]
                )
            }
    }
}
