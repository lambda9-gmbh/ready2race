package de.lambda9.ready2race.backend.app.participant.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantDto
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventDto
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantUpsertDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantRequirementReducedDto
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantForEventRecord
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

fun ParticipantForEventRecord.toDto(): App<Nothing, ParticipantForEventDto> =
    KIO.ok(
        ParticipantForEventDto(
            id = id!!,
            clubId = clubId!!,
            clubName = clubName!!,
            firstname = firstname!!,
            lastname = lastname!!,
            year = year,
            gender = gender!!,
            external = external,
            externalClubName = externalClubName,
            participantRequirementsChecked = participantRequirementsChecked?.map {
                ParticipantRequirementReducedDto(
                    it?.id!!,
                    it.name
                )
            }
        )
    )