package de.lambda9.ready2race.backend.app.raceProperties.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.namedParticipant.control.NamedParticipantRepo
import de.lambda9.ready2race.backend.app.raceCategory.control.RaceCategoryRepo
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesError
import de.lambda9.ready2race.backend.kio.failIf
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.*

object RacePropertiesService {

    fun checkNamedParticipantsExisting(
        namedParticipants: List<UUID>
    ): App<RacePropertiesError, Unit> = KIO.comprehension {
        if (namedParticipants.isEmpty()) {
            KIO.unit
        } else {
            val found = !NamedParticipantRepo.getIfExist(namedParticipants).orDie()
            val notFound = namedParticipants.filter { id -> found.none { it.id == id } }
            if (notFound.isNotEmpty()) {
                KIO.fail(RacePropertiesError.NamedParticipantsUnknown(notFound))
            } else {
                KIO.unit
            }
        }
    }

    fun checkRaceCategoryExisting(
        raceCategory: UUID?
    ): App<RacePropertiesError, Unit> =
        if (raceCategory == null) {
            KIO.unit
        } else {
            RaceCategoryRepo.exists(raceCategory).orDie()
                .failIf(condition = { !it }) {
                    RacePropertiesError.RaceCategoryUnknown
                }.map {}
        }
}