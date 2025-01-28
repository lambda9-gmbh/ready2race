package de.lambda9.ready2race.backend.app.race.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.race.control.RaceRepo
import de.lambda9.ready2race.backend.app.race.control.raceDto
import de.lambda9.ready2race.backend.app.race.control.record
import de.lambda9.ready2race.backend.app.race.entity.RaceDto
import de.lambda9.ready2race.backend.app.race.entity.RaceRequest
import de.lambda9.ready2race.backend.app.race.entity.RaceWithPropertiesSort
import de.lambda9.ready2race.backend.app.raceProperties.control.RacePropertiesHasNamedParticipantRepo
import de.lambda9.ready2race.backend.app.raceProperties.control.RacePropertiesRepo
import de.lambda9.ready2race.backend.app.raceProperties.control.record
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.ktor.http.*
import java.time.LocalDateTime
import java.util.*

object RaceService {

    enum class RaceError : ServiceError {
        RaceNotFound,
        RacePropertiesNotFound;

        override fun respond(): ApiError = when (this) {
            RaceNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Race not Found")
            RacePropertiesNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "RaceProperties not Found")
        }
    }

    fun addRace(
        request: RaceRequest,
        userId: UUID,
        eventId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {

        val raceId = !RaceRepo.create(request.record(userId, eventId)).orDie()
        val racePropertiesId = !RacePropertiesRepo.create(request.raceProperties.record(raceId, null)).orDie()
        RacePropertiesHasNamedParticipantRepo.createMany(request.raceProperties.namedParticipants.map { it.record(racePropertiesId) })

        KIO.ok(ApiResponse.Created(raceId))
    }

    fun pageWithPropertiesByEvent(
        eventId: UUID,
        params: PaginationParameters<RaceWithPropertiesSort>,
    ): App<Nothing, ApiResponse.Page<RaceDto, RaceWithPropertiesSort>> = KIO.comprehension {
        val total = !RaceRepo.countWithPropertiesByEvent(eventId, params.search).orDie()
        val page = !RaceRepo.pageWithPropertiesByEvent(eventId, params).orDie()

        page.forEachM { it.raceDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getRaceWithPropertiesById(
        raceId: UUID
    ): App<RaceError, ApiResponse> = KIO.comprehension {
        val race = !RaceRepo.getWithProperties(raceId).orDie().onNullFail { RaceError.RaceNotFound }
        race.raceDto().map { ApiResponse.Dto(it) }
    }

    fun updateRace(
        request: RaceRequest,
        userId: UUID,
        raceId: UUID
    ): App<RaceError, ApiResponse.NoData> = KIO.comprehension {

        !RaceRepo.update(raceId) {
            template = request.template
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        !RacePropertiesRepo.updateByRaceOrTemplate(raceId) {
            identifier = request.raceProperties.identifier
            name = request.raceProperties.name
            shortName = request.raceProperties.shortName
            description = request.raceProperties.description
            countMales = request.raceProperties.countMales
            countFemales = request.raceProperties.countFemales
            countNonBinary = request.raceProperties.countNonBinary
            countMixed = request.raceProperties.countMixed
            participationFee = request.raceProperties.participationFee
            rentalFee = request.raceProperties.rentalFee
            raceCategory = request.raceProperties.raceCategory
        }.orDie()

        // todo: can the racePropertiesID be returned by the prev. method?
        val racePropertiesId = !RacePropertiesRepo.getIdByRaceId(raceId).orDie().onNullFail { RaceError.RacePropertiesNotFound }

        // delete and re-add the named participant entries
        !RacePropertiesHasNamedParticipantRepo.deleteManyByRaceProperties(racePropertiesId).orDie()
        RacePropertiesHasNamedParticipantRepo.createMany(request.raceProperties.namedParticipants.map { it.record(racePropertiesId) })

        noData
    }

    // Race Properties and Named Participants are deleted by cascade
    fun deleteRace(
        id: UUID,
    ): App<RaceError, ApiResponse.NoData> = KIO.comprehension {

        val deleted = !RaceRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(RaceError.RaceNotFound)
        } else {
            noData
        }
    }
}