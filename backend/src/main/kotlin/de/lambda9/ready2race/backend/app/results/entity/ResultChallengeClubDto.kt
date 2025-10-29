package de.lambda9.ready2race.backend.app.results.entity

import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryDto
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.util.*

data class ResultChallengeClubDto(
    val id: UUID,
    val clubName: String,
    val totalRank: Int,
    val totalResult: Int,
    val relativeRank: Int,
    val relativeResult: Int,
    val teams: List<ResultChallengeClubTeamDto>
) {
    data class ResultChallengeClubTeamDto(
        val competitionRegistrationId: UUID,
        val competitionRegistrationName: String?,
        val result: Int,
        val competitionId: UUID,
        val competitionIdentifier: String,
        val competitionName: String,
        val namedParticipants: List<ResultChallengeClubNamedParticipantDto>,
        val ratingCategoryDto: RatingCategoryDto?
    )

    data class ResultChallengeClubNamedParticipantDto(
        val id: UUID,
        val name: String,
        val participants: List<ResultChallengeClubParticipantDto>
    )

    data class ResultChallengeClubParticipantDto(
        val id: UUID,
        val firstName: String,
        val lastName: String,
        val gender: Gender,
        val year: Int,
        val external: Boolean,
        val externalClubName: String?,
    )
}