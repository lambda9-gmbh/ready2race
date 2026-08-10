package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionDto
import de.lambda9.ready2race.backend.database.generated.tables.Substitution
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupPlaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionViewRecord
import java.time.LocalDateTime
import java.util.*

data class CompetitionSetupRoundWithMatches(
    val setupRoundId: UUID,
    val competitionSetup: UUID,
    val nextRound: UUID?,
    val setupRoundName: String,
    val required: Boolean,
    val isQualification: Boolean,
    val placesOption: String,
    /**
     * Wann diese Runde zum ersten Mal gesetzt wurde. Überlebt das Löschen der Runde und ist damit
     * die einzige Auskunft darüber, ob eine Erzeugung die erste ihrer Art ist.
     */
    val materializedAt: LocalDateTime?,
    val places: List<CompetitionSetupPlaceRecord>,
    val setupMatches: List<CompetitionSetupMatchRecord>,
    val matches: List<CompetitionMatchWithTeams>,
    val substitutions: List<SubstitutionDto>,
)