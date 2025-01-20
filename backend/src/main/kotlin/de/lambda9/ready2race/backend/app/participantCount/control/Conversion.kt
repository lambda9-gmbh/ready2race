package de.lambda9.ready2race.backend.app.participantCount.control

import de.lambda9.ready2race.backend.app.participantCount.entity.ParticipantCountDto
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantCountRecord

fun ParticipantCountDto.record() = ParticipantCountRecord(
    countMales = countMales,
    countFemales = countFemales,
    countNonBinary = countNonBinary,
    countMixed = countMixed,
)