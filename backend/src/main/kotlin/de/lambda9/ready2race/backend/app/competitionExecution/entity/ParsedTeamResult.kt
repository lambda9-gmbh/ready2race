package de.lambda9.ready2race.backend.app.competitionExecution.entity

data class ParsedTeamResult(
    val startNumber: Int,
    val place: Int?,
    val noResultReason: String?
)
