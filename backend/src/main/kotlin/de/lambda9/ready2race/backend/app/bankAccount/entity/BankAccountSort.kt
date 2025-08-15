package de.lambda9.ready2race.backend.app.bankAccount.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import de.lambda9.ready2race.backend.database.generated.tables.references.BANK_ACCOUNT
import org.jooq.Field

enum class BankAccountSort : Sortable {
    ID,
    HOLDER,
    BANK;

    override fun toFields(): List<Field<*>> = when (this) {
        ID -> listOf(BANK_ACCOUNT.ID)
        HOLDER -> listOf(BANK_ACCOUNT.HOLDER)
        BANK -> listOf(BANK_ACCOUNT.BANK)
    }
}