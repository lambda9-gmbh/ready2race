package de.lambda9.ready2race.backend.app.race.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.race.entity.RaceDto
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesDto
import de.lambda9.ready2race.backend.app.race.entity.RaceRequest
import de.lambda9.ready2race.backend.app.raceProperties.control.namedParticipant
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesWithNamedParticipantListDto
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceWithPropertiesRecord
import de.lambda9.tailwind.core.KIO
import java.util.*

fun RaceRequest.record(userId: UUID, eventId: UUID) = RaceRecord(
    id = UUID.randomUUID(),
    event = eventId,
    template = template,
    createdBy = userId,
    updatedBy = userId,
)

fun RaceWithPropertiesRecord.raceDto(): App<Nothing, RaceDto> = KIO.ok(
    RaceDto(
        id = id!!,
        event = event!!,
        raceProperties = RacePropertiesWithNamedParticipantListDto(
            raceProperties = RacePropertiesDto(
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
                raceCategory = raceCategory
            ),
            namedParticipantList = namedParticipantList!!.map { it!!.namedParticipant() } // todo "it!!" safe here?
        ),
        template = template,
    ))