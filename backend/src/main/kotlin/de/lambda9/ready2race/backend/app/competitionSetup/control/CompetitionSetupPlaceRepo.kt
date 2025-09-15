package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupPlaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_GROUP_STATISTIC_EVALUATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_PLACE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.select
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object CompetitionSetupPlaceRepo {
    fun create(records: Collection<CompetitionSetupPlaceRecord>) = COMPETITION_SETUP_PLACE.insert(records)

    fun get(competitionSetupRoundIds: List<UUID>): JIO<List<CompetitionSetupPlaceRecord>> = Jooq.query {
        with(COMPETITION_SETUP_PLACE) {
            selectFrom(this)
                .where(COMPETITION_SETUP_ROUND.`in`(competitionSetupRoundIds))
                .fetch()
        }
    }

    fun getOverlaps(places: List<Pair<UUID, Int>>) = COMPETITION_SETUP_PLACE.select {
        DSL.or(places.map { (roundId, outcome) ->
            COMPETITION_SETUP_ROUND.eq(roundId).and(ROUND_OUTCOME.eq(outcome))
        })
    }
}