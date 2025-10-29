package de.lambda9.ready2race.backend.app.results.entity

import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryDto
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.util.*

data class ResultChallengeParticipantDto(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val rank: Int,
    val result: Int,
    val clubId: UUID,
    val clubName: String,
    val teams: List<ResultChallengeParticipantTeamDto>
) {
    data class ResultChallengeParticipantTeamDto(
        val competitionRegistrationId: UUID,
        val competitionRegistrationName: String?,
        val result: Int,
        val competitionId: UUID,
        val competitionIdentifier: String,
        val competitionName: String,
        val ratingCategoryDto: RatingCategoryDto?
    )
}