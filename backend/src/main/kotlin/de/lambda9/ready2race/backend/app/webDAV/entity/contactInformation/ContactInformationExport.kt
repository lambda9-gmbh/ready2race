package de.lambda9.ready2race.backend.app.webDAV.entity.contactInformation

import java.time.LocalDateTime
import java.util.*

data class ContactInformationExport(
    val id: UUID,
    val name: String,
    val addressZip: String,
    val addressCity: String,
    val addressStreet: String,
    val email: String,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
    val updatedAt: LocalDateTime,
    val updatedBy: UUID?
)