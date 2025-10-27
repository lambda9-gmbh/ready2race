package de.lambda9.ready2race.backend.app.competitionProperties.entity

import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategoryDto

data class CompetitionPropertiesDto(
    val identifier: String,
    val name: String,
    val shortName: String?,
    val description: String?,
    val competitionCategory: CompetitionCategoryDto?,
    val namedParticipants: List<NamedParticipantForCompetitionDto>,
    val fees: List<FeeForCompetitionDto>,
    val lateRegistrationAllowed: Boolean,
    val challengeConfig: CompetitionChallengeConfigDto?,
)