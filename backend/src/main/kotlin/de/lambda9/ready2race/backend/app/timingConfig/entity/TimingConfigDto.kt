package de.lambda9.ready2race.backend.app.timingConfig.entity

import java.util.UUID

data class TimingConfigDto(
    val timingSystem: TimingSystem?,
    val timeTrialResultsUrl: String?,
    val heatsResultsUrl: String?,
    val startlistConfigQualification: UUID?,
    val startlistConfigRounds: UUID?,
    val resultImportConfig: UUID?,
)
