package de.lambda9.ready2race.backend.app.raceTemplate.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategoryDto
import de.lambda9.ready2race.backend.app.raceProperties.control.toDto
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesDto
import de.lambda9.ready2race.backend.app.raceTemplate.entity.RaceTemplateDto
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceTemplateToPropertiesWithNamedParticipantsRecord
import de.lambda9.tailwind.core.extensions.kio.forEachM

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