package de.lambda9.ready2race.backend.app.webDAV.entity.competitionProperties

import java.util.*

data class CompetitionPropertiesHasNamedParticipantExport(
    val competitionProperties: UUID,
    val namedParticipant: UUID,
    val countMales: Int,
    val countFemales: Int,
    val countNonBinary: Int,
    val countMixed: Int
)