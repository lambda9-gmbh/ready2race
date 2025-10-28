package de.lambda9.ready2race.backend.app.competitionProperties.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesChallengeConfigRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES_CHALLENGE_CONFIG
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.update
import java.util.*

object CompetitionPropertiesChallengeConfigRepo {

    fun create(record: CompetitionPropertiesChallengeConfigRecord) =
        COMPETITION_PROPERTIES_CHALLENGE_CONFIG.insert(record)

    fun update(competitionPropertiesId: UUID, f: CompetitionPropertiesChallengeConfigRecord.() -> Unit) =
        COMPETITION_PROPERTIES_CHALLENGE_CONFIG.update(f) { COMPETITION_PROPERTIES.eq(competitionPropertiesId) }
}