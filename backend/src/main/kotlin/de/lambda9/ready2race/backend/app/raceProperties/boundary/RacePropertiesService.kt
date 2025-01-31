package de.lambda9.ready2race.backend.app.raceProperties.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.namedParticipant.control.NamedParticipantRepo
import de.lambda9.ready2race.backend.app.raceCategory.control.RaceCategoryRepo
import de.lambda9.ready2race.backend.count
import de.lambda9.ready2race.backend.kio.failIf
import de.lambda9.ready2race.backend.responses.ApiError
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.ktor.http.*
import java.util.*

object RacePropertiesService {

    sealed interface RacePropertiesError : ServiceError {
        data object RaceCategoryUnknown : RacePropertiesError

        data class NamedParticipantsUnknown(val namedParticipants: List<UUID>) : RacePropertiesError

        override fun respond(): ApiError = when (this) {
            RaceCategoryUnknown -> ApiError(
                status = HttpStatusCode.BadRequest,
                message = "Referenced raceCategory unknown"
            )

            is NamedParticipantsUnknown -> ApiError(
                status = HttpStatusCode.BadRequest,
                message = "${"referenced namedParticipant".count(namedParticipants.size)} unknown",
                details = mapOf("unknownIds" to namedParticipants)
            )
        }
    }


    fun checkNamedParticipantsExisting(
        namedParticipants: List<UUID>
    ): App<RacePropertiesError, Unit> =
        if (namedParticipants.isEmpty()) {
            KIO.unit
        } else {
            NamedParticipantRepo
                .findUnknown(namedParticipants)
                .orDie()
                .failIf(condition = { it.isNotEmpty() }) {
                    RacePropertiesError.NamedParticipantsUnknown(it)
                }.map {}
        }

    fun checkRaceCategoryExisting(
        raceCategory: UUID?
    ): App<RacePropertiesError, Unit> =
        if (raceCategory == null) {
            KIO.unit
        } else {
            RaceCategoryRepo.exists(raceCategory).orDie()
                .failIf(condition = { true }) {
                    RacePropertiesError.RaceCategoryUnknown
                }.map {}
        }
}