package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementForEventDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementUpsertDto
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun ParticipantRequirementUpsertDto.toRecord(userId: UUID): App<Nothing, ParticipantRequirementRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            ParticipantRequirementRecord(
                id = UUID.randomUUID(),
                name = name,
                description = description,
                optional = optional ?: false,
                checkInApp = checkInApp ?: false,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId
            )
        }
    )

fun ParticipantRequirementRecord.toDto(): App<Nothing, ParticipantRequirementDto> = KIO.ok(
    ParticipantRequirementDto(
        id = id,
        name = name,
        description = description,
        optional = optional,
        checkInApp = checkInApp ?: false,
    )
)

fun ParticipantRequirementForEventRecord.toDto(): App<Nothing, ParticipantRequirementForEventDto> = KIO.ok(
    ParticipantRequirementForEventDto(
        id = id!!,
        name = name!!,
        description = description,
        optional = optional!!,
        active = active!!,
        checkInApp = checkInApp!!,
    )
)