package de.lambda9.ready2race.backend.app.caterer.entity

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class CatererTransactionViewDto(
    val id: UUID,
    val catererId: UUID,
    val catererFirstname: String,
    val catererLastname: String,
    val appUserId: UUID,
    val userFirstname: String,
    val userLastname: String,
    val eventId: UUID,
    val price: BigDecimal,
    val createdAt: LocalDateTime
)