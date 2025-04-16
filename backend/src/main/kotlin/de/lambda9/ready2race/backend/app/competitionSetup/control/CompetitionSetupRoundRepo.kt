package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object CompetitionSetupRoundRepo {
    fun create(records: Collection<CompetitionSetupRoundRecord>) = COMPETITION_SETUP_ROUND.insert(records)

    fun delete(key: UUID) = COMPETITION_SETUP_ROUND.delete {
        COMPETITION_SETUP.eq(key).or(COMPETITION_SETUP_TEMPLATE.eq(key))
    }

    fun get(key: UUID): JIO<List<CompetitionSetupRoundRecord>> = Jooq.query {
        with(COMPETITION_SETUP_ROUND) {
            selectFrom(this)
                .where(COMPETITION_SETUP.eq(key).or(COMPETITION_SETUP_TEMPLATE.eq(key)))
                .fetch()
        }
    }
}