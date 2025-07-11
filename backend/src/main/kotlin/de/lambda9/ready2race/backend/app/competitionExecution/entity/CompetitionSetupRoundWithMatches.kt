package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionDto
import de.lambda9.ready2race.backend.database.generated.tables.Substitution
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupPlaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionViewRecord
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
    val matches: List<CompetitionMatchWithTeams>,
    val substitutions: List<SubstitutionDto>,
)