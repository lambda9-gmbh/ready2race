package de.lambda9.ready2race.backend.app.race.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayHasRaceRepo
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.namedParticipant.control.NamedParticipantRepo
import de.lambda9.ready2race.backend.app.race.control.RaceRepo
import de.lambda9.ready2race.backend.app.race.control.raceDto
import de.lambda9.ready2race.backend.app.race.control.record
import de.lambda9.ready2race.backend.app.race.entity.AssignDaysToRaceRequest
import de.lambda9.ready2race.backend.app.race.entity.RaceDto
import de.lambda9.ready2race.backend.app.race.entity.RaceRequest
import de.lambda9.ready2race.backend.app.race.entity.RaceWithPropertiesSort
import de.lambda9.ready2race.backend.app.raceCategory.control.RaceCategoryRepo
import de.lambda9.ready2race.backend.app.raceProperties.control.RacePropertiesHasNamedParticipantRepo
import de.lambda9.ready2race.backend.app.raceProperties.control.RacePropertiesRepo
import de.lambda9.ready2race.backend.app.raceProperties.control.record
import de.lambda9.ready2race.backend.count
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayHasRaceRecord
import de.lambda9.ready2race.backend.failOnFalse
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


    sealed interface RaceError : ServiceError {
        data object RaceNotFound : RaceError
        data object RacePropertiesNotFound : RaceError
        data object RaceCategoryUnknown : RaceError

        data class ReferencedDaysUnknown(val days: List<UUID>) : RaceError
        data class NamedParticipantsUnknown(val namedParticipants: List<UUID>) : RaceError

        override fun respond(): ApiError = when (this) {
            is RaceNotFound -> ApiError(
                status = HttpStatusCode.NotFound,
                message = "Race not found"
            )

            is RacePropertiesNotFound -> ApiError(
                status = HttpStatusCode.NotFound,
                message = "No associated raceProperties found for the race"
            )

            is RaceCategoryUnknown -> ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Referenced raceCategory unknown"
            )

            is ReferencedDaysUnknown -> ApiError(
                status = HttpStatusCode.BadRequest,
                message = "${"referenced race".count(days.size)} unknown",
                details = mapOf("unknownIds" to days)
            )

            is NamedParticipantsUnknown -> ApiError(
                status = HttpStatusCode.BadRequest,
                message = "${"referenced namedParticipants".count(namedParticipants.size)} unknown",
                details = mapOf("unknownIds" to namedParticipants)
            )
        }
    }

    fun addRace(
        request: RaceRequest,
        userId: UUID,
        eventId: UUID,
    ): App<RaceError, ApiResponse.Created> = KIO.comprehension {

        // todo: check if template exists

        request.raceProperties.let { props ->
            if (props.namedParticipants.isNotEmpty()) {
                val unknownNamedParticipants =
                    !NamedParticipantRepo
                        .findUnknown(props.namedParticipants.map { it.namedParticipant })
                        .orDie()
                if (unknownNamedParticipants.isNotEmpty())
                    return@comprehension KIO.fail(RaceError.NamedParticipantsUnknown(unknownNamedParticipants))
            }
            if (props.raceCategory != null) {
                val raceCategoryExists = !RaceCategoryRepo.exists(props.raceCategory).orDie()
                if (!raceCategoryExists) return@comprehension KIO.fail(RaceError.RaceCategoryUnknown)
            }
        }


        val raceId = !RaceRepo.create(request.record(userId, eventId)).orDie()
        val racePropertiesId = !RacePropertiesRepo.create(request.raceProperties.record(raceId, null)).orDie()
        !RacePropertiesHasNamedParticipantRepo.create(request.raceProperties.namedParticipants.map {
            it.record(
                racePropertiesId
            )
        }).orDie()

        KIO.ok(ApiResponse.Created(raceId))
    }

    fun pageWithPropertiesByEvent(
        eventId: UUID,
        params: PaginationParameters<RaceWithPropertiesSort>,
        eventDayId: UUID?
    ): App<Nothing, ApiResponse.Page<RaceDto, RaceWithPropertiesSort>> = KIO.comprehension {
        val total =
            if (eventDayId == null) !RaceRepo.countWithPropertiesByEvent(eventId, params.search).orDie()
            else !RaceRepo.countWithPropertiesByEventAndEventDay(eventId, eventDayId, params.search).orDie()

        val page =
            if (eventDayId == null) !RaceRepo.pageWithPropertiesByEvent(eventId, params).orDie()
            else !RaceRepo.pageWithPropertiesByEventAndEventDay(eventId, eventDayId, params).orDie()

        page.forEachM { it.raceDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getRaceWithProperties(
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

        // todo: check if template exists

        // todo duplicate code with create
        request.raceProperties.let { props ->
            if (props.namedParticipants.isNotEmpty()) {
                val unknownNamedParticipants =
                    !NamedParticipantRepo
                        .findUnknown(props.namedParticipants.map { it.namedParticipant })
                        .orDie()
                if (unknownNamedParticipants.isNotEmpty())
                    return@comprehension KIO.fail(RaceError.NamedParticipantsUnknown(unknownNamedParticipants))
            }
            if (props.raceCategory != null) {
                val raceCategoryExists = !RaceCategoryRepo.exists(props.raceCategory).orDie()
                if (!raceCategoryExists) return@comprehension KIO.fail(RaceError.RaceCategoryUnknown)
            }
        }

        !RaceRepo.update(raceId) {
            template = request.template
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().failOnFalse { RaceError.RaceNotFound }

        // In theory the RacePropertiesRepo functions can't fail because there has to be a "raceProperties" for the "race" to exist
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
        }.orDie().failOnFalse { RaceError.RacePropertiesNotFound }

        val racePropertiesId =
            !RacePropertiesRepo.getIdByRaceId(raceId).orDie().onNullFail { RaceError.RacePropertiesNotFound }

        // delete and re-add the named participant entries
        !RacePropertiesHasNamedParticipantRepo.deleteManyByRaceProperties(racePropertiesId).orDie()
        !RacePropertiesHasNamedParticipantRepo.create(request.raceProperties.namedParticipants.map {
            it.record(
                racePropertiesId
            )
        }).orDie()

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


    fun updateEventDayHasRace(
        request: AssignDaysToRaceRequest,
        userId: UUID,
        raceId: UUID
    ): App<RaceError, ApiResponse.NoData> = KIO.comprehension {

        val raceExists = !RaceRepo.exists(raceId).orDie()
        if (!raceExists) return@comprehension KIO.fail(RaceError.RaceNotFound)

        val unknownDays = !EventDayRepo.findUnknown(request.days).orDie()
        if (unknownDays.isNotEmpty()) return@comprehension KIO.fail(RaceError.ReferencedDaysUnknown(unknownDays))

        !EventDayHasRaceRepo.deleteByRace(raceId).orDie()
        !EventDayHasRaceRepo.create(request.days.map {
            EventDayHasRaceRecord(
                eventDay = it,
                race = raceId,
                createdBy = userId
            )
        }).orDie()

        noData
    }
}
