package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.util.UUID

data class ParsedTeamResult(
    val registrationId: UUID,
    val startNumber: Int?,
    val place: Int?,
    val time: String?,
    val noResultReason: String?
)
