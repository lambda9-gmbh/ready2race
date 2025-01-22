package de.lambda9.ready2race.backend.app.race.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.race.entity.RaceDto
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesDto
import de.lambda9.ready2race.backend.app.race.entity.RaceRequest
import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategoryDto
import de.lambda9.ready2race.backend.app.raceProperties.control.toDto
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceToPropertiesWithNamedParticipantsRecord
import de.lambda9.tailwind.core.KIO
import java.util.*

fun RaceRequest.record(userId: UUID, eventId: UUID) = RaceRecord(
    id = UUID.randomUUID(),
    event = eventId,
    template = template,
    createdBy = userId,
    updatedBy = userId,
)

fun RaceToPropertiesWithNamedParticipantsRecord.raceDto(): App<Nothing, RaceDto> = KIO.ok(
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
            raceCategory = RaceCategoryDto(
                name = categoryName!!,
                description = categoryDescription
            ),
            namedParticipants = namedParticipants!!.map { it!!.toDto() }
        ),
        template = template,
    ))