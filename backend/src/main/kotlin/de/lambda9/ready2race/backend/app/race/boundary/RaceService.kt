package de.lambda9.ready2race.backend.app.race.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.participantCount.control.ParticipantCountRepo
import de.lambda9.ready2race.backend.app.participantCount.control.record
import de.lambda9.ready2race.backend.app.race.control.RaceRepo
import de.lambda9.ready2race.backend.app.race.control.raceDto
import de.lambda9.ready2race.backend.app.race.control.record
import de.lambda9.ready2race.backend.app.race.entity.RaceRequest
import de.lambda9.ready2race.backend.app.racePropertiesHasNamedParticipant.control.RacePropertiesHasNamedParticipantRepo
import de.lambda9.ready2race.backend.app.raceProperties.control.RacePropertiesRepo
import de.lambda9.ready2race.backend.app.raceProperties.control.record
import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesHasNamedParticipantRecord
import de.lambda9.ready2race.backend.http.ApiError
import de.lambda9.ready2race.backend.http.ApiResponse
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.zip
import io.ktor.http.*
import java.util.*

object RaceService {

    enum class RaceError : ServiceError {
        RaceNotFound;

        override fun respond(): ApiError = when (this) {
            RaceNotFound -> ApiError(status = HttpStatusCode.NotFound, message = "Race Not Found")
        }
    }

    fun addRace(
        request: RaceRequest,
        userId: UUID,
        eventId: UUID,
    ): App<Nothing, ApiResponse.Created> = KIO.comprehension {

        val participantCountId =
            if (request.raceProperties.participantCount != null)
                !ParticipantCountRepo.create(request.raceProperties.participantCount.record()).orDie()
            else null

        val racePropertiesId = !RacePropertiesRepo.create(request.raceProperties.record(participantCountId)).orDie()

        val namedParticipantCountIds = !ParticipantCountRepo
            .createMany(request.namedParticipantList.map { it.participantCount.record() })
            .orDie()

        val namedParticipantList = namedParticipantCountIds.zip(request.namedParticipantList.map{it.namedParticipant})

        RacePropertiesHasNamedParticipantRepo.createMany(namedParticipantList.map{ (pc, name) ->
            RacePropertiesHasNamedParticipantRecord(
                raceProperties = racePropertiesId,
                namedParticipant = name,
                participantCount = pc
            )
        })

        val raceId = !RaceRepo.create(request.record(userId, eventId, racePropertiesId)).orDie()

        KIO.ok(ApiResponse.Created(raceId))
    }

    fun getRaceById(
        raceId: UUID
    ): App<RaceError, ApiResponse> = KIO.comprehension {
        val race = !RaceRepo.getWithProperties(raceId).orDie().onNullFail { RaceError.RaceNotFound }
        race.raceDto().map { ApiResponse.Dto(it) }
    }
}