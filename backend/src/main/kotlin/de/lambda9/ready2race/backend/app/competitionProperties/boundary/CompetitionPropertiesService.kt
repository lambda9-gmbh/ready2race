package de.lambda9.ready2race.backend.app.competitionProperties.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.namedParticipant.control.NamedParticipantRepo
import de.lambda9.ready2race.backend.app.competitionCategory.control.CompetitionCategoryRepo
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesError
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesRequestDto
import de.lambda9.ready2race.backend.kio.failIf
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.*

object CompetitionPropertiesService {

    fun checkNamedParticipantsExisting(
        namedParticipants: List<UUID>
    ): App<CompetitionPropertiesError, Unit> = KIO.comprehension {
        if (namedParticipants.isEmpty()) {
            unit
        } else {
            val found = !NamedParticipantRepo.getIfExist(namedParticipants).orDie()
            val notFound = namedParticipants.filter { id -> found.none { it.id == id } }
            if (notFound.isNotEmpty()) {
                KIO.fail(CompetitionPropertiesError.NamedParticipantsUnknown(notFound))
            } else {
                unit
            }
        }
    }

    fun checkCompetitionCategoryExisting(
        competitionCategory: UUID?
    ): App<CompetitionPropertiesError, Unit> =
        if (competitionCategory == null) {
            unit
        } else {
            CompetitionCategoryRepo.exists(competitionCategory).orDie()
                .failIf(condition = { !it }) {
                    CompetitionPropertiesError.CompetitionCategoryUnknown
                }.map {}
        }

    fun checkRequestReferences(
        request: CompetitionPropertiesRequestDto,
    ): App<ServiceError, Unit> = KIO.comprehension {
        !checkNamedParticipantsExisting(request.namedParticipants.map { it.namedParticipant })
        !checkCompetitionCategoryExisting(request.competitionCategory)

        unit
    }
}