package de.lambda9.ready2race.backend.app.webDAV.entity.competitionSetup

import java.util.*

data class CompetitionSetupMatchExport(
    val id: UUID,
    val competitionSetupRound: UUID,
    val competitionSetupGroup: UUID?,
    val weighting: Int,
    val teams: Int?,
    val name: String?,
    val executionOrder: Int,
    val startTimeOffset: Long?
)