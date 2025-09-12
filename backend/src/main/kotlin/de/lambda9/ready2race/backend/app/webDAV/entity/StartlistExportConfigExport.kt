package de.lambda9.ready2race.backend.app.webDAV.entity

import java.time.LocalDateTime
import java.util.*

data class StartlistExportConfigExport(
    val id: UUID,
    val name: String,
    val colParticipantFirstname: String?,
    val colParticipantLastname: String?,
    val colParticipantGender: String?,
    val colParticipantRole: String?,
    val colParticipantYear: String?,
    val colParticipantClub: String?,
    val colClubName: String?,
    val colTeamName: String?,
    val colTeamStartNumber: String?,
    val colMatchName: String?,
    val colMatchStartTime: String?,
    val colRoundName: String?,
    val colCompetitionIdentifier: String?,
    val colCompetitionName: String?,
    val colCompetitionShortName: String?,
    val colCompetitionCategory: String?,
    val colTeamRatingCategory: String?,
    val colTeamClub: String?,
    val colTeamDeregistered: String?,
    val valueTeamDeregistered: String?,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
    val updatedAt: LocalDateTime,
    val updatedBy: UUID?
)