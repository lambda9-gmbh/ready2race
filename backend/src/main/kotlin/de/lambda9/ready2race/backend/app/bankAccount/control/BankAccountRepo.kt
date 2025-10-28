package de.lambda9.ready2race.backend.app.bankAccount.control

import de.lambda9.ready2race.backend.app.bankAccount.entity.BankAccountSort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.database.generated.tables.BankAccount
import de.lambda9.ready2race.backend.database.generated.tables.records.BankAccountRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.BANK_ACCOUNT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import java.util.UUID

object BankAccountRepo {

    private fun BankAccount.searchFields() = listOf(ID, HOLDER, IBAN, BIC, BANK)

    fun exists(id: UUID) = BANK_ACCOUNT.exists { ID.eq(id) }

    fun get(id: UUID) = BANK_ACCOUNT.selectOne { ID.eq(id) }

    fun create(record: BankAccountRecord) = BANK_ACCOUNT.insertReturning(record) { ID }

    fun update(id: UUID, f: BankAccountRecord.() -> Unit) = BANK_ACCOUNT.update(f) { ID.eq(id) }

    fun delete(id: UUID) = BANK_ACCOUNT.delete { ID.eq(id) }

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

    fun getOverlapIds(ids: List<UUID>) = BANK_ACCOUNT.select({ ID }) { ID.`in`(ids) }

    fun create(records: List<BankAccountRecord>) = BANK_ACCOUNT.insert(records)

    fun allAsJson() = BANK_ACCOUNT.selectAsJson()

    fun insertJsonData(data: String) = BANK_ACCOUNT.insertJsonData(data)
}