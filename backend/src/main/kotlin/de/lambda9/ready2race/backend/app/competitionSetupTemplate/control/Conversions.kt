package de.lambda9.ready2race.backend.app.competitionSetupTemplate.control

import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupRoundDto
import de.lambda9.ready2race.backend.app.competitionSetupTemplate.entity.CompetitionSetupTemplateDto
import de.lambda9.ready2race.backend.app.competitionSetupTemplate.entity.CompetitionSetupTemplateOverviewDto
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupTemplateRecord
import de.lambda9.tailwind.core.KIO


fun CompetitionSetupTemplateRecord.toDto(rounds: List<CompetitionSetupRoundDto>) =
    KIO.ok(
        CompetitionSetupTemplateDto(
            id = id,
            name = name,
            description = description,
            rounds = rounds
        )
    )

fun CompetitionSetupTemplateRecord.toOverviewDto() =
    KIO.ok(
        CompetitionSetupTemplateOverviewDto(
            id = id,
            name = name,
            description = description,
        )
    )