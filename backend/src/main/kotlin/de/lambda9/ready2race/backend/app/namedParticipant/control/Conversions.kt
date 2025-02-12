package de.lambda9.ready2race.backend.app.namedParticipant.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantDto
import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.NamedParticipantRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun NamedParticipantRequest.toRecord(userId: UUID): App<Nothing, NamedParticipantRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            NamedParticipantRecord(
                id = UUID.randomUUID(),
                name = name,
                description = description,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId
            )
        }
    )

fun List<NamedParticipantRecord>.namedParticipantDtoList(): App<Nothing, List<NamedParticipantDto>> = KIO.ok(
    this.map {
        NamedParticipantDto(
            id = it.id!!,
            name = it.name,
            description = it.description,
        )
    }
)