package de.lambda9.ready2race.backend.app.results.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.results.control.ResultsRepo
import de.lambda9.ready2race.backend.app.results.control.toDto
import de.lambda9.ready2race.backend.app.results.entity.CompetitionChoiceDto
import de.lambda9.ready2race.backend.app.results.entity.CompetitionHavingResultsSort
import de.lambda9.ready2race.backend.app.results.entity.CompetitionResultData
import de.lambda9.ready2race.backend.app.results.entity.EventResultData
import de.lambda9.ready2race.backend.calls.requests.FileUpload
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.pageResponse
import de.lambda9.ready2race.backend.pdf.PageTemplate
import de.lambda9.ready2race.backend.pdf.document
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.io.ByteArrayOutputStream
import java.util.UUID

object ResultsService {

    fun pageCompetitionsHavingResults(
        eventId: UUID,
        params: PaginationParameters<CompetitionHavingResultsSort>,
    ): App<ServiceError, ApiResponse.Page<CompetitionChoiceDto, CompetitionHavingResultsSort>> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        ResultsRepo.pageCompetitionsHavingResults(eventId, params).orDie().pageResponse { it.toDto() }
    }

    fun generateResultsDocument(
        eventId: UUID,
    ): App<ServiceError, FileUpload> = KIO.comprehension {

        val ids = !CompetitionRepo.getIdsByEvent(eventId).orDie()

        val competitions = !ids.traverse { id ->
            KIO.comprehension {
                val cs = !CompetitionExecutionService.computeCompetitionPlaces(id)

                KIO.ok(
                    EventResultData.CompetitionResultData(

                    )
                )
            }
        }

        val bytes = buildPdf(EventResultData("name", competitions), null)

        KIO.ok(
            FileUpload(
                fileName = "boo.pdf",
                bytes = bytes,
            )
        )
    }

    fun buildPdf(
        data: EventResultData,
        template: PageTemplate?,
    ): ByteArray {
        val doc = document(template) {

        }

        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()

        val bytes = out.toByteArray()
        out.close()

        return bytes
    }

}