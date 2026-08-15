package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.participantRequirement.entity.*
import de.lambda9.ready2race.backend.database.generated.tables.records.CheckedParticipantRequirementRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementForEventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRequirementRecord
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

/**
 * Rückgabetyp ist bewusst `IO<...>` (= `KIO<Any, ...>`) statt des Typalias `App` (= `KIO<JEnv,
 * ...>`): die Funktion greift nie auf die Umgebung zu, bleibt also reine Abbildung ohne DB-Zugriff
 * und lässt sich per `unsafeRunSync()` ohne echtes `JEnv` testen (siehe `CertificateService.
 * participantForEvent` für dieselbe Begründung ausführlicher).
 */
fun ParticipantRequirementUpsertDto.toRecord(userId: UUID): IO<Nothing, ParticipantRequirementRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            ParticipantRequirementRecord(
                id = UUID.randomUUID(),
                name = name,
                description = description,
                publicNote = publicNote,
                optional = optional ?: false,
                checkInApp = checkInApp ?: false,
                publiclyVisible = publiclyVisible ?: false,
                perEventDay = perEventDay ?: false,
                perCompetition = perCompetition ?: false,
                checkEarliestMinutesBefore = checkEarliestMinutesBefore,
                checkLatestMinutesBefore = checkLatestMinutesBefore,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId
            )
        }
    )

// Env-unabhängig aus demselben Grund wie toRecord oben: reine Abbildung ohne Umgebungszugriff,
// deshalb `IO` statt `App` und per `unsafeRunSync()` ohne echtes `JEnv` testbar.
fun ParticipantRequirementRecord.toDto(): IO<Nothing, ParticipantRequirementDto> = KIO.ok(
    ParticipantRequirementDto(
        id = id,
        name = name,
        description = description,
        publicNote = publicNote,
        optional = optional,
        checkInApp = checkInApp ?: false,
        // jOOQ generiert die Spalte trotz NOT-NULL-Constraint als Boolean?, wie auch bei
        // checkInApp; ?: false wahrt hier nur den Kotlin-Typ, ändert nichts am DB-Wert.
        publiclyVisible = publiclyVisible ?: false,
        // Dieselbe Nullbarkeitsfalle wie bei checkInApp/publiclyVisible: die Spalten sind in
        // der Datenbank NOT NULL, jOOQ typisiert sie trotzdem als Boolean?.
        perEventDay = perEventDay ?: false,
        perCompetition = perCompetition ?: false,
        checkEarliestMinutesBefore = checkEarliestMinutesBefore,
        checkLatestMinutesBefore = checkLatestMinutesBefore,
    )
)

// Bleibt bewusst bei `App` statt `IO`: strukturell zwar ebenso eine reine Abbildung wie toRecord/
// toDto oben, aber ohne begleitenden Test, der von der schwächeren Typisierung profitieren würde.
// Umstellung auf `IO` wäre unproblematisch, ist hier aber nicht Teil dieser Änderung.
fun ParticipantRequirementForEventRecord.toDto(): App<Nothing, ParticipantRequirementForEventDto> = KIO.ok(
    ParticipantRequirementForEventDto(
        id = id!!,
        name = name!!,
        description = description,
        optional = optional!!,
        active = active!!,
        checkInApp = checkInApp!!,
        publiclyVisible = publiclyVisible ?: false,
        // Dieselbe Nullbarkeitsfalle wie bei checkInApp: in der Datenbank NOT NULL, von jOOQ
        // trotzdem als Boolean? typisiert.
        perEventDay = perEventDay ?: false,
        perCompetition = perCompetition ?: false,
        requirements = requirements?.filterNotNull()?.map { it.toNamedParticipantRequirementDto() } ?: emptyList(),
    )
)


fun ParticipantRequirementForEventRecord.toRequirementDto() =
    ParticipantRequirementDto(
        id = id!!,
        name = name!!,
        description = description,
        publicNote = publicNote,
        optional = optional!!,
        checkInApp = checkInApp ?: false,
        publiclyVisible = publiclyVisible ?: false,
        perEventDay = perEventDay ?: false,
        perCompetition = perCompetition ?: false,
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

// Bleibt aus demselben Grund wie ParticipantRequirementForEventRecord.toDto oben bei `App`.
fun CheckedParticipantRequirementRecord.toDto(): App<Nothing, CheckedParticipantRequirement> = KIO.ok(
    CheckedParticipantRequirement(
        id = id!!,
        note = note,
        eventDayId = eventDay,
        competitionId = competition,
    )
)