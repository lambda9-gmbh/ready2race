package de.lambda9.ready2race.backend.app.startListConfig.entity

import java.util.UUID

data class StartListConfigDto(
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
    val colTeamRatingCategory: String?,
    val colTeamClub: String?,
    val colMatchName: String?,
    val colMatchStartTime: String?,
    val colRoundName: String?,
    val colCompetitionIdentifier: String?,
    val colCompetitionName: String?,
    val colCompetitionShortName: String?,
    val colCompetitionCategory: String?,
)
