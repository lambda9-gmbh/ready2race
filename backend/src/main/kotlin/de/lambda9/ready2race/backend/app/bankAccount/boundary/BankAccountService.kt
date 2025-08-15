package de.lambda9.ready2race.backend.app.bankAccount.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.bankAccount.control.BankAccountRepo
import de.lambda9.ready2race.backend.app.bankAccount.control.PayeeBankAccountRepo
import de.lambda9.ready2race.backend.app.bankAccount.control.toDto
import de.lambda9.ready2race.backend.app.bankAccount.control.toRecord
import de.lambda9.ready2race.backend.app.bankAccount.entity.AssignBankAccountRequest
import de.lambda9.ready2race.backend.app.bankAccount.entity.AssignedBankAccountDto
import de.lambda9.ready2race.backend.app.bankAccount.entity.BankAccountDto
import de.lambda9.ready2race.backend.app.bankAccount.entity.BankAccountError
import de.lambda9.ready2race.backend.app.bankAccount.entity.BankAccountRequest
import de.lambda9.ready2race.backend.app.bankAccount.entity.BankAccountSort
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.UUID

object BankAccountService {

    fun getAssigned(
        event: UUID?
    ): App<ServiceError, ApiResponse.Dto<AssignedBankAccountDto>> = KIO.comprehension {
        if (event != null) {
            !EventService.checkEventExisting(event)
        }

        val payee = !PayeeBankAccountRepo.getByEvent(event).orDie()
        val assigned = payee?.let { !BankAccountRepo.get(it.bankAccount).orDie() }
        val dto = assigned?.toDto()?.not()

        KIO.ok(
            ApiResponse.Dto(
                AssignedBankAccountDto(
                    assigned = dto
                )
            )
        )
    }

    fun assignAccount(
        request: AssignBankAccountRequest,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        if (request.event != null) {
            !EventService.checkEventExisting(request.event)
        }
        if (request.bankAccount != null) {
            !BankAccountRepo.exists(request.bankAccount).orDie().onFalseFail { BankAccountError.NotFound }
            val record = !request.toRecord(userId)
            !PayeeBankAccountRepo.upsert(record).orDie()
        } else {
            !PayeeBankAccountRepo.deleteByEvent(request.event).orDie()
        }

        noData
    }

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