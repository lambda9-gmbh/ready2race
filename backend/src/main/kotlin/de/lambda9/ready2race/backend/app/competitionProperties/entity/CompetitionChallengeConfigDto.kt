package de.lambda9.ready2race.backend.app.competitionProperties.entity

import java.time.LocalDateTime

data class CompetitionChallengeConfigDto(
    val resultConfirmationImageRequired: Boolean,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
)