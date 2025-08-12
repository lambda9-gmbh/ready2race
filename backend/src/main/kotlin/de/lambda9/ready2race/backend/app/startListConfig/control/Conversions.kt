package de.lambda9.ready2race.backend.app.startListConfig.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigDto
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.StartlistExportConfigRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.UUID

fun StartListConfigRequest.toRecord(userId: UUID): App<Nothing, StartlistExportConfigRecord> = KIO.ok(
    LocalDateTime.now().let { now ->
        StartlistExportConfigRecord(
            id = UUID.randomUUID(),
            name = name,
            colParticipantFirstname = colParticipantFirstname,
            colParticipantLastname = colParticipantLastname,
            colParticipantGender = colParticipantGender,
            colParticipantRole = colParticipantRole,
            colParticipantYear = colParticipantYear,
            colParticipantClub = colParticipantClub,
            colClubName = colClubName,
            colTeamName = colTeamName,
            colTeamStartNumber = colTeamStartNumber,
            colTeamRatingCategory = colTeamRatingCategory,
            colTeamClub = colTeamClub,
            colTeamDeregistered = colTeamDeregistered,
            valueTeamDeregistered = valueTeamDeregistered,
            colMatchName = colMatchName,
            colMatchStartTime = colMatchStartTime,
            colRoundName = colRoundName,
            colCompetitionIdentifier = colCompetitionIdentifier,
            colCompetitionName = colCompetitionName,
            colCompetitionShortName = colCompetitionShortName,
            colCompetitionCategory = colCompetitionCategory,
            createdAt = now,
            createdBy = userId,
            updatedAt = now,
            updatedBy = userId,
        )
    }
)

fun StartlistExportConfigRecord.toDto(): App<Nothing, StartListConfigDto> = KIO.ok(
    StartListConfigDto(
        id = id,
        name = name,
        colParticipantFirstname = colParticipantFirstname,
        colParticipantLastname = colParticipantLastname,
        colParticipantGender = colParticipantGender,
        colParticipantRole = colParticipantRole,
        colParticipantYear = colParticipantYear,
        colParticipantClub = colParticipantClub,
        colClubName = colClubName,
        colTeamName = colTeamName,
        colTeamStartNumber = colTeamStartNumber,
        colTeamRatingCategory = colTeamRatingCategory,
        colTeamClub = colTeamClub,
        colTeamDeregistered = colTeamDeregistered,
        valueTeamDeregistered = valueTeamDeregistered,
        colMatchName = colMatchName,
        colMatchStartTime = colMatchStartTime,
        colRoundName = colRoundName,
        colCompetitionIdentifier = colCompetitionIdentifier,
        colCompetitionName = colCompetitionName,
        colCompetitionShortName = colCompetitionShortName,
        colCompetitionCategory = colCompetitionCategory,
    )
)