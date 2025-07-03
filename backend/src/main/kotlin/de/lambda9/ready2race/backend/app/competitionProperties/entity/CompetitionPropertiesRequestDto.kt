package de.lambda9.ready2race.backend.app.competitionProperties.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.notEmpty
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection
import java.util.*

data class CompetitionPropertiesRequestDto(
    val identifier: String,
    val name: String,
    val shortName: String?,
    val description: String?,
    val competitionCategory: UUID?,
    val namedParticipants: List<NamedParticipantForCompetitionRequestDto>,
    val fees: List<FeeForCompetitionRequestDto>,
    val setupTemplate: UUID?, // Only relevant for add/edit template and add competition
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
            this::fees validate collection
        )

    companion object {
        val example
            get() = CompetitionPropertiesRequestDto(
                identifier = "001",
                name = "Name",
                shortName = "N",
                description = "Description of name",
                competitionCategory = UUID.randomUUID(),
                namedParticipants = listOf(NamedParticipantForCompetitionRequestDto.example),
                fees = listOf(FeeForCompetitionRequestDto.example),
                setupTemplate = UUID.randomUUID(),
            )
    }
}