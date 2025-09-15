package de.lambda9.ready2race.backend.app.webDAV.entity.competitionSetup

import java.util.*

data class CompetitionSetupParticipantExport(
    val id: UUID,
    val competitionSetupMatch: UUID?,
    val competitionSetupGroup: UUID?,
    val seed: Int,
    val ranking: Int
)