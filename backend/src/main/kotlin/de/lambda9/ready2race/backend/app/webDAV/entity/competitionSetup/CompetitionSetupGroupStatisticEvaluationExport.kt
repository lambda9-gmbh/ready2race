package de.lambda9.ready2race.backend.app.webDAV.entity.competitionSetup

import java.util.*

data class CompetitionSetupGroupStatisticEvaluationExport(
    val competitionSetupRound: UUID,
    val name: String,
    val priority: Int,
    val rankByBiggest: Boolean,
    val ignoreBiggestValues: Int,
    val ignoreSmallestValues: Int,
    val asAverage: Boolean
)