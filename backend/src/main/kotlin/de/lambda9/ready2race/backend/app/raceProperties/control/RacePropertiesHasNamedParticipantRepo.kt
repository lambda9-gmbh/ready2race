package de.lambda9.ready2race.backend.app.raceProperties.control

import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesContainingNamedParticipant
import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesHasNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.RACE_PROPERTIES_HAS_NAMED_PARTICIPANT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object RacePropertiesHasNamedParticipantRepo {

    fun create(
        records: List<RacePropertiesHasNamedParticipantRecord>
    ): JIO<Int> = Jooq.query {
        batchInsert(records)
            .execute()
            .sum()
    }

    fun deleteManyByRaceProperties(
        racePropertiesId: UUID,
    ): JIO<Int> = Jooq.query {
        with(RACE_PROPERTIES_HAS_NAMED_PARTICIPANT) {
            deleteFrom(this)
                .where(RACE_PROPERTIES.eq(racePropertiesId))
                .execute()
        }
    }

    fun getByNamedParticipant(
        namedParticipant: UUID
    ): JIO<List<RacePropertiesContainingNamedParticipant>> = Jooq.query {
        select(
            RACE_PROPERTIES.RACE_TEMPLATE,
            RACE_PROPERTIES.RACE,
            RACE_PROPERTIES.NAME,
            RACE_PROPERTIES.SHORT_NAME
        ).from(RACE_PROPERTIES_HAS_NAMED_PARTICIPANT)
            .join(RACE_PROPERTIES)
            .on(RACE_PROPERTIES_HAS_NAMED_PARTICIPANT.RACE_PROPERTIES.eq(RACE_PROPERTIES.ID))
            .where(RACE_PROPERTIES_HAS_NAMED_PARTICIPANT.NAMED_PARTICIPANT.eq(namedParticipant))
            .fetch()
            .map {
                RacePropertiesContainingNamedParticipant(
                    raceTemplateId = it[RACE_PROPERTIES.RACE_TEMPLATE],
                    raceId = it[RACE_PROPERTIES.RACE],
                    name = it[RACE_PROPERTIES.NAME]!!,
                    shortName = it[RACE_PROPERTIES.SHORT_NAME]
                )
            }
    }
}
