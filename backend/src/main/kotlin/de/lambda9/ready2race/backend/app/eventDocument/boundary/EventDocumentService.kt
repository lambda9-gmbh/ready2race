package de.lambda9.ready2race.backend.app.eventDocument.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.eventDocument.control.EventDocumentRepo
import de.lambda9.ready2race.backend.app.eventDocument.control.toDto
import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentDto
import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentError
import de.lambda9.ready2race.backend.app.eventDocument.entity.EventDocumentViewSort
import de.lambda9.ready2race.backend.kio.failIf
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.*

object EventDocumentService {

    fun page(
        params: PaginationParameters<EventDocumentViewSort>,
    ): App<Nothing, ApiResponse.Page<EventDocumentDto, EventDocumentViewSort>> = KIO.comprehension {
        val total = !EventDocumentRepo.count(params.search).orDie()
        val page = !EventDocumentRepo.page(params).orDie()

        page.forEachM { it.toDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun downloadDocument(
        id: UUID,
    ): App<EventDocumentError, ApiResponse.File> =
        EventDocumentRepo.getDownload(id).orDie().onNullFail { EventDocumentError.NotFound }
            .map {
                ApiResponse.File(
                    name = it.name!!,
                    bytes = it.data!!,
                )
            }

    fun deleteDocument(
        id: UUID,
    ): App<EventDocumentError, ApiResponse.NoData> =
        EventDocumentRepo.delete(id).orDie().failIf({ it < 1 }) { EventDocumentError.NotFound }
            .map { ApiResponse.NoData }
}