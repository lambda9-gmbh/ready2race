package de.lambda9.ready2race.backend.app.bankAccount.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.bankAccount.control.BankAccountRepo
import de.lambda9.ready2race.backend.app.bankAccount.control.toDto
import de.lambda9.ready2race.backend.app.bankAccount.control.toRecord
import de.lambda9.ready2race.backend.app.bankAccount.entity.BankAccountDto
import de.lambda9.ready2race.backend.app.bankAccount.entity.BankAccountError
import de.lambda9.ready2race.backend.app.bankAccount.entity.BankAccountRequest
import de.lambda9.ready2race.backend.app.bankAccount.entity.BankAccountSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.UUID

object BankAccountService {

    fun addAccount(
        request: BankAccountRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {

        val record = !request.toRecord(userId)
        BankAccountRepo.create(record).orDie().map {
            ApiResponse.Created(it)
        }
    }

    fun page(
        params: PaginationParameters<BankAccountSort>
    ): App<Nothing, ApiResponse.Page<BankAccountDto, BankAccountSort>> = KIO.comprehension {

        val total = !BankAccountRepo.count(params.search).orDie()
        val page = !BankAccountRepo.page(params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun updateAccount(
        id: UUID,
        request: BankAccountRequest,
        userId: UUID,
    ): App<BankAccountError, ApiResponse.NoData> =
        BankAccountRepo.update(id) {
            holder = request.holder
            iban = request.iban
            bic = request.bic
            bank = request.bank
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()
            .onNullFail { BankAccountError.NotFound }
            .map { ApiResponse.NoData }

    fun deleteAccount(
        id: UUID,
    ): App<BankAccountError, ApiResponse.NoData> =
        BankAccountRepo.delete(id).orDie()
            .failIf({ it < 1 }) { BankAccountError.NotFound }
            .map { ApiResponse.NoData }
}