package de.lambda9.ready2race.backend.app.namedParticipant.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.namedParticipant.control.NamedParticipantRepo
import de.lambda9.ready2race.backend.app.namedParticipant.control.namedParticipantDto
import de.lambda9.ready2race.backend.app.namedParticipant.control.toRecord
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantDto
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantError
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantRequest
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantSort
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesHasNamedParticipantRepo
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionsOrTemplatesContainingNamedParticipant
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.UUID

object NamedParticipantService {

    fun addNamedParticipant(
        request: NamedParticipantRequest,
        userId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val record = !request.toRecord(userId)
        NamedParticipantRepo.create(record).orDie().map {
            ApiResponse.Created(it)
        }
    }

    fun page(
        params: PaginationParameters<NamedParticipantSort>
    ): App<Nothing, ApiResponse.Page<NamedParticipantDto, NamedParticipantSort>> = KIO.comprehension {
        val total = !NamedParticipantRepo.count(params.search).orDie()
        val page = !NamedParticipantRepo.page(params).orDie()

        page.forEachM { it.namedParticipantDto() }.map{
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun updateNamedParticipant(
        namedParticipantId: UUID,
        request: NamedParticipantRequest,
        userId: UUID,
    ): App<NamedParticipantError, ApiResponse.NoData> =
        NamedParticipantRepo.update(namedParticipantId) {
            name = request.name
            description = request.description
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie()
            .onNullFail { NamedParticipantError.NamedParticipantNotFound }
            .map { ApiResponse.NoData }

    fun deleteNamedParticipant(
        namedParticipantId: UUID,
    ): App<NamedParticipantError, ApiResponse.NoData> = KIO.comprehension {

        // Checks if NamedParticipant is referenced by either Competition or CompetitionTemplate - If so, it fails
        val propertiesContainingNamedParticipants =
            !CompetitionPropertiesHasNamedParticipantRepo.getByNamedParticipant(namedParticipantId).orDie()
                .map { list ->
                    CompetitionsOrTemplatesContainingNamedParticipant(
                        templates = list.filter { it.competitionTemplateId != null }.ifEmpty { null },
                        competitions = list.filter { it.competitionId != null }.ifEmpty { null },
                    )
                }
        if (!propertiesContainingNamedParticipants.competitions.isNullOrEmpty() || !propertiesContainingNamedParticipants.templates.isNullOrEmpty()) {
            return@comprehension KIO.fail(
                NamedParticipantError.NamedParticipantIsInUse(
                    propertiesContainingNamedParticipants
                )
            )
        }


        val deleted = !NamedParticipantRepo.delete(namedParticipantId).orDie()

        if (deleted < 1) {
            KIO.fail(NamedParticipantError.NamedParticipantNotFound)
        } else {
            noData
        }
    }
}