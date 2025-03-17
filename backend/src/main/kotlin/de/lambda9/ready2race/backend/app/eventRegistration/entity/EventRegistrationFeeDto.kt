package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.math.BigDecimal
import java.util.*

data class EventRegistrationFeeDto (
    val id: UUID,
    val label: String,
    val description: String?,
    val required: Boolean,
    val amount: BigDecimal
)
