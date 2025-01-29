package de.lambda9.ready2race.backend.app.namedParticipant.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.namedParticipant.control.NamedParticipantRepo
import de.lambda9.ready2race.backend.app.namedParticipant.control.namedParticipantDtoList
import de.lambda9.ready2race.backend.app.namedParticipant.control.record
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantDto
import de.lambda9.ready2race.backend.failOnFalse
import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.ktor.http.*
import java.util.UUID

object NamedParticipantService {

    enum class NamedParticipantError : ServiceError {
        NamedParticipantNotFound;

        override fun respond(): ApiError = when (this) {
            NamedParticipantNotFound -> ApiError(
                status = HttpStatusCode.NotFound,
                message = "NamedParticipant not Found"
            )
        }
    }

    fun addNamedParticipant(
        request: NamedParticipantDto
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {
        val namedParticipantId = !NamedParticipantRepo.create(request.record()).orDie()
        KIO.ok(ApiResponse.Created(namedParticipantId))
    }


    fun getNamedParticipantList(): App<Nothing, ApiResponse.Dto<List<NamedParticipantDto>>> = KIO.comprehension {
        val namedParticipantList = !NamedParticipantRepo.getMany().orDie()

        namedParticipantList.namedParticipantDtoList().map { ApiResponse.Dto(it) }
    }

    fun updateNamedParticipant(
        request: NamedParticipantDto,
        namedParticipantId: UUID
    ): App<NamedParticipantError, ApiResponse.NoData> = KIO.comprehension {
        !NamedParticipantRepo.update(namedParticipantId) {
            name = request.name
            description = request.description
        }.orDie().failOnFalse { NamedParticipantError.NamedParticipantNotFound }

        noData
    }

    fun deleteNamedParticipant(
        namedParticipantId: UUID,
    ): App<NamedParticipantError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !NamedParticipantRepo.delete(namedParticipantId).orDie()

        if (deleted < 1) {
            KIO.fail(NamedParticipantError.NamedParticipantNotFound)
        } else {
            noData
        }
    }
}