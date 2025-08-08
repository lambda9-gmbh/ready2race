package de.lambda9.ready2race.backend.app.competitionDeregistration.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionDeregistration.entity.CompetitionDeregistrationRequest
import de.lambda9.ready2race.backend.app.competitionExecution.entity.UpdateCompetitionMatchTeamResultRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionDeregistrationRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun CompetitionDeregistrationRequest.toRecord(
    userId: UUID,
    competitionRegistration: UUID,
    competitionSetupRound: UUID?
): App<Nothing, CompetitionDeregistrationRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            CompetitionDeregistrationRecord(
                competitionRegistration = competitionRegistration,
                competitionSetupRound = competitionSetupRound,
                reason = reason,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        }
    )
