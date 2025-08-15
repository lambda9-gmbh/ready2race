package de.lambda9.ready2race.backend.app.results.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.results.control.ResultsRepo
import de.lambda9.ready2race.backend.app.results.control.toDto
import de.lambda9.ready2race.backend.app.results.entity.CompetitionChoiceDto
import de.lambda9.ready2race.backend.app.results.entity.CompetitionHavingResultsSort
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.pageResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.UUID

object ResultsService {

    fun pageCompetitionsHavingResults(
        eventId: UUID,
        params: PaginationParameters<CompetitionHavingResultsSort>,
    ): App<ServiceError, ApiResponse.Page<CompetitionChoiceDto, CompetitionHavingResultsSort>> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        ResultsRepo.pageCompetitionsHavingResults(eventId, params).orDie().pageResponse { it.toDto() }
    }

}