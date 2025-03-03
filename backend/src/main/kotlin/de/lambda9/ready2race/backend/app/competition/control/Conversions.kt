package de.lambda9.ready2race.backend.app.competition.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionDto
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesDto
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionRequest
import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategoryDto
import de.lambda9.ready2race.backend.app.competitionProperties.control.toDto
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionViewRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

fun CompetitionRequest.toRecord(userId: UUID, eventId: UUID): App<Nothing, CompetitionRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            CompetitionRecord(
                id = UUID.randomUUID(),
                event = eventId,
                template = template,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        }
    )

fun CompetitionViewRecord.toDto(): App<Nothing, CompetitionDto> = KIO.comprehension {
    val feeDtos = !fees!!.toList().forEachM {
        it!!.toDto()
    }.orDie()

    val namedParticipantDtos = !namedParticipants!!.toList().forEachM {
        it!!.toDto()
    }.orDie()

    KIO.ok(
        CompetitionDto(
            id = id!!,
            event = event!!,
            properties = CompetitionPropertiesDto(
                identifier = identifier!!,
                name = name!!,
                shortName = shortName,
                description = description,
                competitionCategory = if (categoryId !== null) {
                    CompetitionCategoryDto(
                        id = categoryId!!,
                        name = categoryName!!,
                        description = categoryDescription
                    )
                } else null,
                namedParticipants = namedParticipantDtos,
                fees = feeDtos
            ),
            template = template,
        )
    )
}