package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.util.UUID

data class CompetitionMatchTeamDto(
    val registrationId: UUID,
    val teamNumber: Int,
    val clubId: UUID,
    val clubName: String,
    val name: String?,
    val startNumber: Int,
    val place: Int?,
    val deregistered: Boolean,
    val deregistrationReason: String?,
    val failed: Boolean,
    val failedReason: String?,
)