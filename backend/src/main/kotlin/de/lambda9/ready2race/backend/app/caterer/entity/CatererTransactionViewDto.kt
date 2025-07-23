package de.lambda9.ready2race.backend.app.caterer.entity

import de.lambda9.ready2race.backend.calls.pagination.Sortable
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

enum class CatererTransactionViewSort : Sortable {
    CATERER_NAME,
    USER_NAME,
    PRICE,
    CREATED_AT;

    override fun toFields(): List<org.jooq.Field<*>> = when (this) {
        CATERER_NAME -> listOf(de.lambda9.ready2race.backend.database.generated.tables.references.CATERER_TRANSACTION_VIEW.CATERER_LASTNAME, de.lambda9.ready2race.backend.database.generated.tables.references.CATERER_TRANSACTION_VIEW.CATERER_FIRSTNAME)
        USER_NAME -> listOf(de.lambda9.ready2race.backend.database.generated.tables.references.CATERER_TRANSACTION_VIEW.USER_LASTNAME, de.lambda9.ready2race.backend.database.generated.tables.references.CATERER_TRANSACTION_VIEW.USER_FIRSTNAME)
        PRICE -> listOf(de.lambda9.ready2race.backend.database.generated.tables.references.CATERER_TRANSACTION_VIEW.PRICE)
        CREATED_AT -> listOf(de.lambda9.ready2race.backend.database.generated.tables.references.CATERER_TRANSACTION_VIEW.CREATED_AT)
    }
}