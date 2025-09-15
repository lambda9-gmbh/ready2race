package de.lambda9.ready2race.backend.app.webDAV.entity.competitionProperties

import java.util.*

data class CompetitionPropertiesExport(
    val id: UUID,
    val competition: UUID?,
    val competitionTemplate: UUID?,
    val identifier: String,
    val name: String,
    val shortName: String?,
    val description: String?,
    val competitionCategory: UUID?,
    val lateRegistrationAllowed: Boolean?
)