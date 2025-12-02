package de.lambda9.ready2race.backend.app.matchResultImportConfig.entity

import java.util.UUID

data class MatchResultImportConfigDto(
    val id: UUID,
    val name: String,
    val colTeamStartNumber: String,
    val colTeamPlace: String?,
    val colTeamTime: String?,
)
