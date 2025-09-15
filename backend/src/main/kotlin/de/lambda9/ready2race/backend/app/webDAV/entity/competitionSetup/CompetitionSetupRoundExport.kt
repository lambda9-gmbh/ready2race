package de.lambda9.ready2race.backend.app.webDAV.entity.competitionSetup

import java.util.*

data class CompetitionSetupRoundExport(
    val id: UUID,
    val competitionSetup: UUID?,
    val competitionSetupTemplate: UUID?,
    val nextRound: UUID?,
    val name: String,
    val required: Boolean,
    val useDefaultSeeding: Boolean,
    val placesOption: String
)