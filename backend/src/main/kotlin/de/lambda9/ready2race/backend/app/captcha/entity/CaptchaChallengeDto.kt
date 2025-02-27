package de.lambda9.ready2race.backend.app.captcha.entity

import java.util.UUID

data class CaptchaChallengeDto(
    val id: UUID,
    val imgSrc: String,
    val solutionMin: Int,
    val solutionMax: Int,
    val handleToHeightRatio: Float,
    val start: Int
)