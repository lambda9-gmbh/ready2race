package de.lambda9.ready2race.backend.app.bankAccount.control

import de.lambda9.ready2race.backend.app.bankAccount.entity.BankAccountSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.BankAccount
import de.lambda9.ready2race.backend.database.generated.tables.records.BankAccountRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.BANK_ACCOUNT
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.metaSearch
import de.lambda9.ready2race.backend.database.page
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object BankAccountRepo {

    private fun BankAccount.searchFields() = listOf(ID, HOLDER, IBAN, BIC, BANK)

    fun create(record: BankAccountRecord) = BANK_ACCOUNT.insertReturning(record) { ID }

    fun update(id: UUID, f: BankAccountRecord.() -> Unit) = BANK_ACCOUNT.update(f) { ID.eq(id) }

    fun delete(id: UUID) = BANK_ACCOUNT.delete { ID.eq(id)}

    fun count(search: String?): JIO<Int> = Jooq.query {
        with(BANK_ACCOUNT) {
            fetchCount(this, search.metaSearch(searchFields()))
        }
    }

    fun page(params: PaginationParameters<BankAccountSort>): JIO<List<BankAccountRecord>> = Jooq.query {
        with(BANK_ACCOUNT) {
            selectFrom(this)
                .page(params, searchFields())
                .fetch()
        }
    }
}