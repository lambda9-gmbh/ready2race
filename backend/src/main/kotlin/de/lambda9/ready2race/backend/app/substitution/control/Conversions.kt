package de.lambda9.ready2race.backend.app.substitution.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.substitution.entity.SubstitutionRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.UUID

fun SubstitutionRequest.toRecord(userId: UUID, orderForRound: Long): App<Nothing, SubstitutionRecord> = KIO.ok(
    LocalDateTime.now().let { now ->
        SubstitutionRecord(
            id = UUID.randomUUID(),
            competitionRegistration = competitionRegistrationId,
            competitionSetupRound = competitionSetupRound,
            participantOut = participantOut,
            participantIn = participantIn,
            reason = reason,
            orderForRound = orderForRound,
            createdAt = now,
            createdBy = userId,
            updatedAt = now,
            updatedBy = userId,
        )
    }
)