package de.lambda9.ready2race.backend.app.webDAV.entity.event

import java.time.LocalDateTime
import java.util.*

data class ContactInformationUsageExport(
    val contactInformation: UUID,
    val event: UUID?,
    val assignedAt: LocalDateTime,
    val assignedBy: UUID?
)