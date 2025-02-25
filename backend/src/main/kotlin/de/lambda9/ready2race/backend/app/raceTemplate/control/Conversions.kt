package de.lambda9.ready2race.backend.app.raceTemplate.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategoryDto
import de.lambda9.ready2race.backend.app.raceProperties.control.toDto
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesDto
import de.lambda9.ready2race.backend.app.raceTemplate.entity.RaceTemplateDto
import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceTemplateToPropertiesWithNamedParticipantsRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import java.util.*

fun RaceTemplateToPropertiesWithNamedParticipantsRecord.toDto(): App<Nothing, RaceTemplateDto> =
    namedParticipants!!.toList().forEachM {
        it!!.toDto()
    }.map {
        RaceTemplateDto(
            id = id!!,
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
        )
    }

fun RaceTemplateToPropertiesWithNamedParticipantsRecord.applyRaceProperties(raceId: UUID): App<Nothing, RacePropertiesRecord> =
    KIO.ok(
        RacePropertiesRecord(
            id = UUID.randomUUID(),
            race = raceId,
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
            raceCategory = categoryId
        ),
    )

fun RaceTemplateToPropertiesWithNamedParticipantsRecord.toUpdateFunction(): RacePropertiesRecord.() -> Unit = let {
    {
        identifier = it.identifier!!
        name = it.name!!
        shortName = it.shortName
        description = it.description
        countMales = it.countMales!!
        countFemales = it.countFemales!!
        countNonBinary = it.countNonBinary!!
        countMixed = it.countMixed!!
        participationFee = it.participationFee!!
        rentalFee = it.rentalFee!!
        raceCategory = it.categoryId
    }
}
