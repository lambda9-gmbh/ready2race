package de.lambda9.ready2race.backend.app.competitionTemplate.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategoryDto
import de.lambda9.ready2race.backend.app.competitionProperties.control.toDto
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesDto
import de.lambda9.ready2race.backend.app.competitionTemplate.entity.CompetitionTemplateDto
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionTemplateToPropertiesWithNamedParticipantsRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.forEachM
import java.util.*

fun CompetitionTemplateToPropertiesWithNamedParticipantsRecord.toDto(): App<Nothing, CompetitionTemplateDto> =
    namedParticipants!!.toList().forEachM {
        it!!.toDto()
    }.map {
        CompetitionTemplateDto(
            id = id!!,
            properties = CompetitionPropertiesDto(
                identifier = identifier!!,
                name = name!!,
                shortName = shortName,
                description = description,
                countMales = countMales!!,
                countFemales = countFemales!!,
                countNonBinary = countNonBinary!!,
                countMixed = countMixed!!,
                competitionCategory = if (categoryId !== null) {
                    CompetitionCategoryDto(
                        id = categoryId!!,
                        name = categoryName!!,
                        description = categoryDescription
                    )
                } else null,
                namedParticipants = it
            ),
        )
    }

fun CompetitionTemplateToPropertiesWithNamedParticipantsRecord.applyCompetitionProperties(competitionId: UUID): App<Nothing, CompetitionPropertiesRecord> =
    KIO.ok(
        CompetitionPropertiesRecord(
            id = UUID.randomUUID(),
            competition = competitionId,
            identifier = identifier!!,
            name = name!!,
            shortName = shortName,
            description = description,
            countMales = countMales!!,
            countFemales = countFemales!!,
            countNonBinary = countNonBinary!!,
            countMixed = countMixed!!,
            competitionCategory = categoryId
        ),
    )

fun CompetitionTemplateToPropertiesWithNamedParticipantsRecord.toUpdateFunction(): CompetitionPropertiesRecord.() -> Unit = let {
    {
        identifier = it.identifier!!
        name = it.name!!
        shortName = it.shortName
        description = it.description
        countMales = it.countMales!!
        countFemales = it.countFemales!!
        countNonBinary = it.countNonBinary!!
        countMixed = it.countMixed!!
        competitionCategory = it.categoryId
    }
}
