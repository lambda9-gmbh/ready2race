package de.lambda9.ready2race.backend.app.startListConfig.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.startListConfig.control.StartListConfigRepo
import de.lambda9.ready2race.backend.app.startListConfig.control.toDto
import de.lambda9.ready2race.backend.app.startListConfig.control.toRecord
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigDto
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigError
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigRequest
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
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

object StartListConfigService {

    fun addConfig(
        request: StartListConfigRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        StartListConfigRepo.create(record).orDie().createdResponse()
    }

    fun page(
        params: PaginationParameters<StartListConfigSort>,
    ): App<Nothing, ApiResponse.Page<StartListConfigDto, StartListConfigSort>> = KIO.comprehension {
        val total = !StartListConfigRepo.count(params.search).orDie()
        val page = !StartListConfigRepo.page(params).orDie()

        page.traverse { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun updateConfig(
        id: UUID,
        request: StartListConfigRequest,
        userId: UUID,
    ): App<StartListConfigError, ApiResponse.NoData> =
        StartListConfigRepo.update(id) {
            name = request.name
            colParticipantFirstname = request.colParticipantFirstname
            colParticipantLastname = request.colParticipantLastname
            colParticipantGender = request.colParticipantGender
            colParticipantRole = request.colParticipantRole
            colParticipantYear = request.colParticipantYear
            colParticipantClub = request.colParticipantClub
            colClubName = request.colClubName
            colTeamName = request.colTeamName
            colTeamStartNumber = request.colTeamStartNumber
            colTeamRatingCategory = request.colTeamRatingCategory
            colTeamClub = request.colTeamClub
            colTeamDeregistered = request.colTeamDeregistered
            valueTeamDeregistered = request.valueTeamDeregistered
            colMatchName = request.colMatchName
            colMatchStartTime = request.colMatchStartTime
            colRoundName = request.colRoundName
            colCompetitionIdentifier = request.colCompetitionIdentifier
            colCompetitionName = request.colCompetitionName
            colCompetitionShortName = request.colCompetitionShortName
            colCompetitionCategory = request.colCompetitionCategory
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()
            .onNullFail { StartListConfigError.NotFound }
            .noDataResponse()

    fun deleteConfig(
        id: UUID,
    ): App<StartListConfigError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !StartListConfigRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(StartListConfigError.NotFound)
        } else {
            noData
        }
    }
}