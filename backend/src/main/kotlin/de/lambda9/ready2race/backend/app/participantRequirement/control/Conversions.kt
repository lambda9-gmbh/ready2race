package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.participantRequirement.entity.*
import de.lambda9.ready2race.backend.database.generated.tables.records.CheckedParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

// Env-unabhängig (KIO<Any?, ...> statt App<Nothing, ...>): reine Abbildung ohne DB-Zugriff,
// dadurch per unsafeRunSync ohne echten Jooq-Kontext testbar.
fun ParticipantRequirementUpsertDto.toRecord(userId: UUID): KIO<Any?, Nothing, ParticipantRequirementRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            ParticipantRequirementRecord(
                id = UUID.randomUUID(),
                name = name,
                description = description,
                optional = optional ?: false,
                checkInApp = checkInApp ?: false,
                publiclyVisible = publiclyVisible ?: false,
                checkEarliestMinutesBefore = checkEarliestMinutesBefore,
                checkLatestMinutesBefore = checkLatestMinutesBefore,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId
            )
        }
    )

// Env-unabhängig aus demselben Grund wie toRecord oben.
fun ParticipantRequirementRecord.toDto(): KIO<Any?, Nothing, ParticipantRequirementDto> = KIO.ok(
    ParticipantRequirementDto(
        id = id,
        name = name,
        description = description,
        optional = optional,
        checkInApp = checkInApp ?: false,
        // jOOQ generiert die Spalte trotz NOT-NULL-Constraint als Boolean?, wie auch bei
        // checkInApp; ?: false wahrt hier nur den Kotlin-Typ, ändert nichts am DB-Wert.
        publiclyVisible = publiclyVisible ?: false,
        checkEarliestMinutesBefore = checkEarliestMinutesBefore,
        checkLatestMinutesBefore = checkLatestMinutesBefore,
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
        publiclyVisible = publiclyVisible ?: false,
        requirements = requirements?.filterNotNull()?.map { it.toNamedParticipantRequirementDto() } ?: emptyList(),
    )
)


fun ParticipantRequirementForEventRecord.toRequirementDto() =
    ParticipantRequirementDto(
        id = id!!,
        name = name!!,
        description = description,
        optional = optional!!,
        checkInApp = checkInApp ?: false,
        publiclyVisible = publiclyVisible ?: false,
        checkEarliestMinutesBefore = checkEarliestMinutesBefore,
        checkLatestMinutesBefore = checkLatestMinutesBefore,
    )

fun ParticipantRequirementForEventRecord.toNamedParticipantRequirementDto(namedParticipantId: UUID) =
    CompetitionRegistrationNamedParticipantRequirementDto(
        id = id!!,
        name = name!!,
        description = description,
        optional = optional!!,
        checkInApp = checkInApp ?: false,
        qrCodeRequired = requirements?.find { it!!.id == namedParticipantId }?.qrCodeRequired ?: false
    )


fun ParticipantRequirementNamedParticipantRecord.toNamedParticipantRequirementDto(): NamedParticipantRequirementForEventDto =
    NamedParticipantRequirementForEventDto(
        id = id!!,
        name = name!!,
        qrCodeRequired = qrCodeRequired ?: false,
    )

fun CheckedParticipantRequirementRecord.toDto(): App<Nothing, CheckedParticipantRequirement> = KIO.ok(
    CheckedParticipantRequirement(
        id = id!!,
        note = note,
    )
)