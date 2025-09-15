package de.lambda9.ready2race.backend.app.webDAV.entity.event

import java.time.LocalDateTime
import java.util.*

data class PayeeBankAccountExport(
    val bankAccount: UUID,
    val event: UUID?,
    val assignedAt: LocalDateTime,
    val assignedBy: UUID?
)