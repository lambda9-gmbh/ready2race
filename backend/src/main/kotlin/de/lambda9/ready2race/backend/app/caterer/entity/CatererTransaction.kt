package de.lambda9.ready2race.backend.app.caterer.entity

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class CatererTransaction(
    val id: UUID,
    val catererId: UUID,
    val appUserId: UUID,
    val price: BigDecimal,
    val eventId: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val createdBy: UUID
)