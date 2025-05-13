package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.update
import java.util.UUID

object CompetitionSetupRepo {
    fun create(record: CompetitionSetupRecord) = COMPETITION_SETUP.insertReturning(record) { COMPETITION_PROPERTIES }

    fun update(competitionPropertiesId: UUID, f: CompetitionSetupRecord.() -> Unit) =
        COMPETITION_SETUP.update(f) { COMPETITION_PROPERTIES.eq(competitionPropertiesId) }
}