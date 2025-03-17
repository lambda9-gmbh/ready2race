package de.lambda9.ready2race.backend.app.participant.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.club.entity.ParticipantDto
import de.lambda9.ready2race.backend.app.club.entity.ParticipantUpsertDto
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun ParticipantUpsertDto.toRecord(userId: UUID, clubId: UUID): App<Nothing, ParticipantRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            ParticipantRecord(
                id = UUID.randomUUID(),
                club = clubId,
                firstname = this.firstname,
                lastname = this.lastname,
                year = this.year,
                gender = this.gender,
                phone = this.phone,
                external = this.external,
                externalClubName = this.externalClubName?.trim()?.takeIf { it.isNotBlank() },
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        }
    )

fun ParticipantRecord.participantDto(): App<Nothing, ParticipantDto> = KIO.ok(
    ParticipantDto(
        id = id,
        firstname = firstname,
        lastname = lastname,
        year = year,
        gender = gender,
        phone = phone,
        external = external,
        externalClubName = externalClubName,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
)