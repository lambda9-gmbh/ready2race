package de.lambda9.ready2race.backend.app.competitionProperties.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.notEmpty
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.selfValidator
import java.util.*

data class CompetitionPropertiesRequest(
    val identifier: String,
    val name: String,
    val shortName: String?,
    val description: String?,
    val competitionCategory: UUID?,
    val namedParticipants: List<NamedParticipantForCompetitionRequestDto>,
    val fees: List<FeeForCompetitionRequestDto>,
    val lateRegistrationAllowed: Boolean,
    val setupTemplate: UUID?, // Only relevant for add/edit template and add competition
    val challengeConfig: CompetitionChallengeConfigRequest?,
    val ratingCategoryRequired: Boolean,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::identifier validate notBlank,
            this::name validate notBlank,
            this::shortName validate notBlank,
            this::description validate notBlank,
            this::namedParticipants validate allOf(
                collection,
                noDuplicates(
                    NamedParticipantForCompetitionRequestDto::namedParticipant,
                ),
                notEmpty
            ),
            this::fees validate allOf(
                collection,
                noDuplicates(
                    FeeForCompetitionRequestDto::fee,
                ),
            ),
            this::challengeConfig validate selfValidator
        )

    companion object {
        val example
            get() = CompetitionPropertiesRequest(
                identifier = "001",
                name = "Name",
                shortName = "N",
                description = "Description of name",
                competitionCategory = UUID.randomUUID(),
                namedParticipants = listOf(NamedParticipantForCompetitionRequestDto.example),
                fees = listOf(FeeForCompetitionRequestDto.example),
                lateRegistrationAllowed = true,
                setupTemplate = UUID.randomUUID(),
                challengeConfig = CompetitionChallengeConfigRequest.example,
                ratingCategoryRequired = false,
            )
    }
}