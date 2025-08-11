package de.lambda9.ready2race.backend.app.namedParticipant.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesHasNamedParticipantRepo
import de.lambda9.ready2race.backend.app.competitionProperties.entity.splitTemplatesAndCompetitions
import de.lambda9.ready2race.backend.app.namedParticipant.control.NamedParticipantRepo
import de.lambda9.ready2race.backend.app.namedParticipant.control.namedParticipantDto
import de.lambda9.ready2race.backend.app.namedParticipant.control.toRecord
import de.lambda9.ready2race.backend.app.namedParticipant.entity.*
import de.lambda9.ready2race.backend.app.participantRequirement.control.ParticipantRequirementForEventRepo
import de.lambda9.ready2race.backend.app.participantRequirement.control.ParticipantRequirementRepo
import de.lambda9.ready2race.backend.app.participantRequirement.entity.EventParticipantRequirementDto
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*

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

        page.traverse { it.namedParticipantDto() }.map {
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
            .onNullFail { NamedParticipantError.NotFound }
            .map { ApiResponse.NoData }

    fun deleteNamedParticipant(
        namedParticipantId: UUID,
    ): App<NamedParticipantError, ApiResponse.NoData> = KIO.comprehension {

        // Checks if NamedParticipant is referenced by either Competition or CompetitionTemplate - If so, it fails
        val propertiesContainingNamedParticipants =
            !CompetitionPropertiesHasNamedParticipantRepo.getByNamedParticipant(namedParticipantId).orDie()
                .map { list -> list.splitTemplatesAndCompetitions() }
        if (!propertiesContainingNamedParticipants.competitions.isNullOrEmpty() || !propertiesContainingNamedParticipants.templates.isNullOrEmpty()) {
            return@comprehension KIO.fail(
                NamedParticipantError.NamedParticipantInUse(propertiesContainingNamedParticipants)
            )
        }

        val deleted = !NamedParticipantRepo.delete(namedParticipantId).orDie()

        if (deleted < 1) {
            KIO.fail(NamedParticipantError.NotFound)
        } else {
            noData
        }
    }

    private fun getNamedParticipantWithRequirements(
        namedParticipantId: UUID,
        eventId: UUID
    ): App<NamedParticipantError, ApiResponse.Dto<NamedParticipantWithRequirementsDto>> = KIO.comprehension {
        val namedParticipant = !NamedParticipantRepo.get(namedParticipantId).orDie()
            .onNullFail { NamedParticipantError.NotFound }

        val requirements =
            !ParticipantRequirementForEventRepo.getRequirementsForNamedParticipant(eventId, namedParticipantId).orDie()

        val requirementDtos = !requirements.traverse { record ->
            ParticipantRequirementRepo.get(record.participantRequirement).orDie()
                .onNullFail { NamedParticipantError.NotFound }
                .map { requirement ->
                    EventParticipantRequirementDto(
                        requirementId = requirement.id,
                        requirementName = requirement.name,
                        requirementDescription = requirement.description,
                        namedParticipantId = record.namedParticipant,
                        qrCodeRequired = record.qrCodeRequired!!
                    )
                }
        }

        val hasQrCodeRequirement =
            requirementDtos.any { it.namedParticipantId == namedParticipantId && it.qrCodeRequired }

        val dto = NamedParticipantWithRequirementsDto(
            id = namedParticipant.id,
            name = namedParticipant.name,
            description = namedParticipant.description,
            requirements = requirementDtos.filter { it.namedParticipantId == namedParticipantId },
            qrCodeRequired = hasQrCodeRequirement
        )

        KIO.ok(ApiResponse.Dto(dto))
    }

    fun getNamedParticipantsForEvent(
        eventId: UUID
    ): App<NamedParticipantError, ApiResponse.ListDto<NamedParticipantWithRequirementsDto>> = KIO.comprehension {
        val namedParticipants = !NamedParticipantRepo.findAll().orDie()

        val result = !namedParticipants.traverse { participant ->
            getNamedParticipantWithRequirements(participant.id, eventId)
        }

        KIO.ok(ApiResponse.ListDto(result.map { it.dto }))
    }
}