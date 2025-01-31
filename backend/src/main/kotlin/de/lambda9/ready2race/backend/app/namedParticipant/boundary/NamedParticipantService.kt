package de.lambda9.ready2race.backend.app.namedParticipant.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.namedParticipant.control.NamedParticipantRepo
import de.lambda9.ready2race.backend.app.namedParticipant.control.namedParticipantDtoList
import de.lambda9.ready2race.backend.app.namedParticipant.control.record
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantDto
import de.lambda9.ready2race.backend.app.raceProperties.control.RacePropertiesHasNamedParticipantRepo
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacesOrTemplatesContainingNamedParticipant
import de.lambda9.ready2race.backend.count
import de.lambda9.ready2race.backend.kio.failOnFalse
import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.ktor.http.*
import java.util.UUID

object NamedParticipantService {

    sealed interface NamedParticipantError : ServiceError {
        data object NamedParticipantNotFound : NamedParticipantError

        data class NamedParticipantIsInUse(val racesOrTemplates: RacesOrTemplatesContainingNamedParticipant) :
            NamedParticipantError

        override fun respond(): ApiError = when (this) {
            NamedParticipantNotFound -> ApiError(
                status = HttpStatusCode.NotFound,
                message = "NamedParticipant not Found"
            )

            is NamedParticipantIsInUse -> ApiError(
                status = HttpStatusCode.Conflict,
                message = "NamedParticipant is contained in " +
                    if (racesOrTemplates.races != null) {
                        "race".count(racesOrTemplates.races.size) +
                            if (racesOrTemplates.templates != null) {
                                " and "
                            } else {
                                ""
                            }
                    } else {
                        ""
                    } +
                    if (racesOrTemplates.templates != null) {
                        "templates".count(racesOrTemplates.templates.size)
                    } else {
                        ""
                    },
                details = mapOf("entitiesContainingNamedParticipants" to racesOrTemplates)
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
    ): App<NamedParticipantError, ApiResponse.NoData> =
        NamedParticipantRepo.update(namedParticipantId) {
            name = request.name
            description = request.description
        }.orDie()
            .failOnFalse { NamedParticipantError.NamedParticipantNotFound }
            .map { ApiResponse.NoData }

    fun deleteNamedParticipant(
        namedParticipantId: UUID,
    ): App<NamedParticipantError, ApiResponse.NoData> = KIO.comprehension {

        // Checks if NamedParticipant is referenced by either Race or RaceTemplate - If so, it fails
        val propertiesContainingNamedParticipants =
            !RacePropertiesHasNamedParticipantRepo.getByNamedParticipant(namedParticipantId).orDie()
                .map { list ->
                    RacesOrTemplatesContainingNamedParticipant(
                        templates = list.filter { it.raceTemplateId != null }.ifEmpty { null },
                        races = list.filter { it.raceId != null }.ifEmpty { null },
                    )
                }
        if (!propertiesContainingNamedParticipants.races.isNullOrEmpty() || !propertiesContainingNamedParticipants.templates.isNullOrEmpty()) {
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