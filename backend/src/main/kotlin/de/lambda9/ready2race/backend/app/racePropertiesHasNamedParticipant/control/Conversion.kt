package de.lambda9.ready2race.backend.app.racePropertiesHasNamedParticipant.control

import de.lambda9.ready2race.backend.app.racePropertiesHasNamedParticipant.entity.RacePropertiesHasNamedParticipantDto
import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesHasNamedParticipantRecord
import java.util.*

fun RacePropertiesHasNamedParticipantDto.record(racePropertiesId: UUID) = RacePropertiesHasNamedParticipantRecord(
    raceProperties = racePropertiesId,
    namedParticipant = namedParticipant,
    countMales = countMales,
    countFemales = countFemales,
    countNonBinary = countNonBinary,
    countMixed = countMixed
)