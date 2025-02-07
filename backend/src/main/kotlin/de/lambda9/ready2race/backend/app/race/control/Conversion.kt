package de.lambda9.ready2race.backend.app.race.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.race.entity.RaceDto
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesDto
import de.lambda9.ready2race.backend.app.race.entity.RaceRequest
import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategoryDto
import de.lambda9.ready2race.backend.app.raceProperties.control.toDto
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceToPropertiesWithNamedParticipantsRecord
import de.lambda9.tailwind.core.extensions.kio.forEachM
import java.util.*

fun RaceRequest.record(userId: UUID, eventId: UUID) = RaceRecord(
    id = UUID.randomUUID(),
    event = eventId,
    template = template,
    createdBy = userId,
    updatedBy = userId,
)

fun RaceToPropertiesWithNamedParticipantsRecord.toDto(): App<Nothing, RaceDto> =
    namedParticipants!!.toList().forEachM {
        it!!.toDto()
    }.map {
        RaceDto(
            id = id!!,
            event = event!!,
            properties = RacePropertiesDto(
                identifier = identifier!!,
                name = name!!,
                shortName = shortName,
                description = description,
                countMales = countMales!!,
                countFemales = countFemales!!,
                countNonBinary = countNonBinary!!,
                countMixed = countMixed!!,
                participationFee = participationFee!!,
                rentalFee = rentalFee!!,
                raceCategory = if (categoryId !== null) {
                    RaceCategoryDto(
                        id = categoryId!!,
                        name = categoryName!!,
                        description = categoryDescription
                    )
                } else null,
                namedParticipants = it
            ),
            template = template,
        )
    }
