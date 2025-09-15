package de.lambda9.ready2race.backend.app.webDAV.entity.competitionSetup

import java.util.*

data class CompetitionSetupGroupExport(
    val id: UUID,
    val weighting: Int,
    val teams: Int?,
    val name: String?
)