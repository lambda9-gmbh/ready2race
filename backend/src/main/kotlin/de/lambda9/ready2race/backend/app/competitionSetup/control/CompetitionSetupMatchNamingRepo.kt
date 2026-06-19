package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchNamingRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH_NAMING
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object CompetitionSetupMatchNamingRepo {
    fun create(records: Collection<CompetitionSetupMatchNamingRecord>) =
        COMPETITION_SETUP_MATCH_NAMING.insert(records)

    fun get(competitionSetupRoundIds: List<UUID>): JIO<List<CompetitionSetupMatchNamingRecord>> = Jooq.query {
        with(COMPETITION_SETUP_MATCH_NAMING) {
            selectFrom(this)
                .where(COMPETITION_SETUP_ROUND.`in`(competitionSetupRoundIds))
                .fetch()
        }
    }

    fun getForRoundAndCount(competitionSetupRoundId: UUID, participantCount: Int): JIO<List<CompetitionSetupMatchNamingRecord>> =
        Jooq.query {
            with(COMPETITION_SETUP_MATCH_NAMING) {
                selectFrom(this)
                    .where(COMPETITION_SETUP_ROUND.eq(competitionSetupRoundId))
                    .and(PARTICIPANT_COUNT.eq(participantCount))
                    .fetch()
            }
        }
}
