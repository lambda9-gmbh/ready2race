package de.lambda9.ready2race.backend.app.namedParticipant.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantDto
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.NamedParticipantRecord
import de.lambda9.tailwind.core.KIO

fun NamedParticipantRequest.record() = NamedParticipantRecord(
    name = name,
    description = description,
)

fun List<NamedParticipantRecord>.namedParticipantDtoList(): App<Nothing, List<NamedParticipantDto>> = KIO.ok(
    this.map {
        NamedParticipantDto(
            id = it.id!!,
            name = it.name!!,
            description = it.description,
        )
    }
)