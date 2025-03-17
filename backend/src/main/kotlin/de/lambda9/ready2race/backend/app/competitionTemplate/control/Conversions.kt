package de.lambda9.ready2race.backend.app.competitionTemplate.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategoryDto
import de.lambda9.ready2race.backend.app.competitionProperties.control.toDto
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesDto
import de.lambda9.ready2race.backend.app.competitionTemplate.entity.CompetitionTemplateDto
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionTemplateViewRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.*

fun CompetitionTemplateViewRecord.toDto(): App<Nothing, CompetitionTemplateDto> = KIO.comprehension {
    val feeDtos = !fees!!.toList().traverse {
        it!!.toDto()
    }.orDie()

    val namedParticipantDtos = !namedParticipants!!.toList().traverse {
        it!!.toDto()
    }.orDie()

    KIO.ok(
        CompetitionTemplateDto(
            id = id!!,
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
        )
    )
}


fun CompetitionTemplateViewRecord.applyCompetitionProperties(competitionId: UUID): App<Nothing, CompetitionPropertiesRecord> =
    KIO.ok(
        CompetitionPropertiesRecord(
            id = UUID.randomUUID(),
            competition = competitionId,
            identifier = identifier!!,
            name = name!!,
            shortName = shortName,
            description = description,
            competitionCategory = categoryId
        ),
    )

fun CompetitionTemplateViewRecord.toUpdateFunction(): CompetitionPropertiesRecord.() -> Unit = let {
    {
        identifier = it.identifier!!
        name = it.name!!
        shortName = it.shortName
        description = it.description
        competitionCategory = it.categoryId
    }
}
