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
import de.lambda9.ready2race.backend.app.caterer.entity.CatererTransactionRequest
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.util.*

object CatererService {

    fun createCateringTransaction(
        transaction: CatererTransactionRequest,
        catererId: UUID
    ): App<CatererError, ApiResponse.NoData> = KIO.comprehension {
        !AppUserRepo.exists(transaction.appUserId).orDie()
            .onFalseFail { CatererError.UserNotFound }

        val record = !transaction.toRecord(catererId)
        !CatererRepo.create(record).orDie()

        noData
    }

    fun pageByEventId(
        eventId: UUID,
        params: PaginationParameters<CatererTransactionViewSort>
    ): App<ServiceError, ApiResponse.Page<CatererTransactionViewDto, CatererTransactionViewSort>> = KIO.comprehension {
        val total = !CatererRepo.countByEventId(eventId, params.search).orDie()
        val page = !CatererRepo.pageByEventId(eventId, params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }
}