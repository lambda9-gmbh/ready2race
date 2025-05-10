package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupGroupStatisticEvaluationRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_GROUP_STATISTIC_EVALUATION
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object CompetitionSetupGroupStatisticEvaluationRepo {
    fun create(records: Collection<CompetitionSetupGroupStatisticEvaluationRecord>) =
        COMPETITION_SETUP_GROUP_STATISTIC_EVALUATION.insert(records)

    fun get(competitionSetupRoundIds: List<UUID>): JIO<List<CompetitionSetupGroupStatisticEvaluationRecord>> =
        Jooq.query {
            with(COMPETITION_SETUP_GROUP_STATISTIC_EVALUATION) {
                selectFrom(this)
                    .where(COMPETITION_SETUP_ROUND.`in`(competitionSetupRoundIds))
                    .fetch()
            }
        }
}