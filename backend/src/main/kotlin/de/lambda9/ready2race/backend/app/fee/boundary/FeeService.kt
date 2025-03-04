package de.lambda9.ready2race.backend.app.fee.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesHasFeeRepo
import de.lambda9.ready2race.backend.app.competitionProperties.entity.splitTemplatesAndCompetitions
import de.lambda9.ready2race.backend.app.fee.control.FeeRepo
import de.lambda9.ready2race.backend.app.fee.control.feeDto
import de.lambda9.ready2race.backend.app.fee.control.toRecord
import de.lambda9.ready2race.backend.app.fee.entity.FeeDto
import de.lambda9.ready2race.backend.app.fee.entity.FeeError
import de.lambda9.ready2race.backend.app.fee.entity.FeeRequest
import de.lambda9.ready2race.backend.app.fee.entity.FeeSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.UUID

object FeeService {

    fun addFee(
        request: FeeRequest,
        userId: UUID
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        FeeRepo.create(record).orDie().map {
            ApiResponse.Created(it)
        }
    }

    fun page(
        params: PaginationParameters<FeeSort>
    ): App<Nothing, ApiResponse.Page<FeeDto, FeeSort>> = KIO.comprehension {
        val total = !FeeRepo.count(params.search).orDie()
        val page = !FeeRepo.page(params).orDie()

        page.forEachM { it.feeDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun updateFee(
        feeId: UUID,
        request: FeeRequest,
        userId: UUID,
    ): App<FeeError, ApiResponse.NoData> =
        FeeRepo.update(feeId) {
            name = request.name
            description = request.description
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()
            .onNullFail { FeeError.NotFound }
            .map { ApiResponse.NoData }

    fun deleteFee(
        feeId: UUID,
    ): App<FeeError, ApiResponse.NoData> = KIO.comprehension {

        // Checks if Fee is referenced by either Competition or CompetitionTemplate - If so, it fails
        val propertiesContainingFees =
            !CompetitionPropertiesHasFeeRepo.getByFee(feeId).orDie()
                .map { it.splitTemplatesAndCompetitions() }

        if (propertiesContainingFees.containsEntries()) {
            return@comprehension KIO.fail(
                FeeError.FeeInUse(propertiesContainingFees)
            )
        }

        val deleted = !FeeRepo.delete(feeId).orDie()

        if (deleted < 1) {
            KIO.fail(FeeError.NotFound)
        } else {
            noData
        }
    }

}