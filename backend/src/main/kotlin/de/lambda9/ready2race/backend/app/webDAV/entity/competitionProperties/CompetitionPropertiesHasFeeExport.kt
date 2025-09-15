package de.lambda9.ready2race.backend.app.webDAV.entity.competitionProperties

import java.math.BigDecimal
import java.util.*

data class CompetitionPropertiesHasFeeExport(
    val id: UUID,
    val competitionProperties: UUID,
    val fee: UUID,
    val required: Boolean,
    val amount: BigDecimal,
    val lateAmount: BigDecimal?
)