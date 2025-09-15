package de.lambda9.ready2race.backend.app.webDAV.entity.competitionSetup

import java.util.*

data class CompetitionSetupPlaceExport(
    val competitionSetupRound: UUID,
    val roundOutcome: Int,
    val place: Int
)