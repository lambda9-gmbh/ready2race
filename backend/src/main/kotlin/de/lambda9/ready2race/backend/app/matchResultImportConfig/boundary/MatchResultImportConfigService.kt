package de.lambda9.ready2race.backend.app.matchResultImportConfig.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.matchResultImportConfig.control.MatchResultImportConfigRepo
import de.lambda9.ready2race.backend.app.matchResultImportConfig.control.toDto
import de.lambda9.ready2race.backend.app.matchResultImportConfig.control.toRecord
import de.lambda9.ready2race.backend.app.matchResultImportConfig.entity.MatchResultImportConfigDto
import de.lambda9.ready2race.backend.app.matchResultImportConfig.entity.MatchResultImportConfigError
import de.lambda9.ready2race.backend.app.matchResultImportConfig.entity.MatchResultImportConfigRequest
import de.lambda9.ready2race.backend.app.matchResultImportConfig.entity.MatchResultImportConfigSort
import de.lambda9.ready2race.backend.app.startListConfig.control.StartListConfigRepo
import de.lambda9.ready2race.backend.app.startListConfig.control.toDto
import de.lambda9.ready2race.backend.app.startListConfig.control.toRecord
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigDto
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigError
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigRequest
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigSort
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.responses.createdResponse
import de.lambda9.ready2race.backend.calls.responses.noDataResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.UUID

object MatchResultImportConfigService {

    fun addConfig(
        request: MatchResultImportConfigRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        MatchResultImportConfigRepo.create(record).orDie().createdResponse()
    }

    fun page(
        params: PaginationParameters<MatchResultImportConfigSort>,
    ): App<Nothing, ApiResponse.Page<MatchResultImportConfigDto, MatchResultImportConfigSort>> = KIO.comprehension {
        val total = !MatchResultImportConfigRepo.count(params.search).orDie()
        val page = !MatchResultImportConfigRepo.page(params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun updateConfig(
        id: UUID,
        request: MatchResultImportConfigRequest,
        userId: UUID,
    ): App<MatchResultImportConfigError, ApiResponse.NoData> =
        MatchResultImportConfigRepo.update(id) {
            name = request.name
            colTeamStartNumber = request.colTeamStartNumber
            colTeamPlace = request.colTeamPlace
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()
            .onNullFail { MatchResultImportConfigError.NotFound }
            .noDataResponse()

    fun deleteConfig(
        id: UUID,
    ): App<MatchResultImportConfigError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !MatchResultImportConfigRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(MatchResultImportConfigError.NotFound)
        } else {
            noData
        }
    }
}