package de.lambda9.ready2race.backend.app.caterer.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.caterer.control.CatererRepo
import de.lambda9.ready2race.backend.app.caterer.control.toDto
import de.lambda9.ready2race.backend.app.caterer.control.toRecord
import de.lambda9.ready2race.backend.app.caterer.entity.CatererError
import de.lambda9.ready2race.backend.app.caterer.entity.CatererTransactionViewDto
import de.lambda9.ready2race.backend.app.caterer.entity.CatererTransactionViewSort
import de.lambda9.ready2race.backend.app.caterer.entity.NewCatererTransactionDTO
import de.lambda9.ready2race.backend.calls.pagination.Pagination
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.*

object CatererService {

    fun createCateringTransaction(
        transaction: NewCatererTransactionDTO,
        catererId: UUID
    ): App<CatererError, ApiResponse.NoData> = KIO.comprehension {
        !AppUserRepo.exists(transaction.appUserId).orDie()
            .onFalseFail { CatererError.UserNotFound }

        val record = !transaction.toRecord(catererId)
        !CatererRepo.create(record).orDie()

        noData
    }

    fun getByEventId(
        eventId: UUID,
        pagination: PaginationParameters<CatererTransactionViewSort>
    ): App<ServiceError, ApiResponse.Page<CatererTransactionViewDto, CatererTransactionViewSort>> = KIO.comprehension {
        val count = !CatererRepo.countByEventId(eventId, pagination.search).orDie()
        val records = !CatererRepo.pageByEventId(eventId, pagination).orDie()
        val dtos = records.map { it.toDto() }

        KIO.ok(
            ApiResponse.Page(
                data = dtos,
                pagination = Pagination(
                    total = count,
                    limit = pagination.limit,
                    offset = pagination.offset,
                    sort = pagination.sort,
                    search = pagination.search
                )
            )
        )
    }
}