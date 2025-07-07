package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupPlaceRecord
import java.util.*

data class CompetitionSetupRoundWithMatches(
    val setupRoundId: UUID,
    val competitionSetup: UUID,
    val nextRound: UUID?,
    val setupRoundName: String,
    val required: Boolean,
    val placesOption: String,
    val places: List<CompetitionSetupPlaceRecord>,
    val setupMatches: List<CompetitionSetupMatchRecord>,
    val matches: List<CompetitionMatchWithTeams>
)