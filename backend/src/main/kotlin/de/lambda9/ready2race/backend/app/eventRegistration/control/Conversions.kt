package de.lambda9.ready2race.backend.app.eventRegistration.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationParticipantUpsertDto
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun EventRegistrationParticipantUpsertDto.toRecord(userId: UUID, clubId: UUID): App<Nothing, ParticipantRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            ParticipantRecord(
                id = UUID.randomUUID(),
                club = clubId,
                firstname = this.firstname,
                lastname = this.lastname,
                year = this.year,
                gender = this.gender,
                external = this.external,
                externalClubName = this.externalClubName?.trim()?.takeIf { it.isNotBlank() },
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        }
    )