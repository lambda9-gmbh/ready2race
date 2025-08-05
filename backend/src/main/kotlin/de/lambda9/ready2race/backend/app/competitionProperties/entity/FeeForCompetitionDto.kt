package de.lambda9.ready2race.backend.app.competitionProperties.entity

import java.math.BigDecimal
import java.util.UUID

data class FeeForCompetitionDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val required: Boolean,
    val amount: BigDecimal,
    val lateAmount: BigDecimal?,
)