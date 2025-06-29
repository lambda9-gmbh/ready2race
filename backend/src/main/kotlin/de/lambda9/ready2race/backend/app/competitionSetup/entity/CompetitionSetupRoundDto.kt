package de.lambda9.ready2race.backend.app.competitionSetup.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.flatMap
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isNotValue
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isNull
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isValue
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.select

data class CompetitionSetupRoundDto(
    val name: String,
    val required: Boolean,
    val matches: List<CompetitionSetupMatchDto>?,
    val groups: List<CompetitionSetupGroupDto>?,
    val statisticEvaluations: List<CompetitionSetupGroupStatisticEvaluationDto>?,
    val useDefaultSeeding: Boolean,
    val placesOption: CompetitionSetupPlacesOption,
    val places: List<CompetitionSetupPlaceDto>?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::name validate notBlank,
        this::matches validate allOf(
            collection,
            noDuplicates(
                CompetitionSetupMatchDto::weighting
            ),
            flatMap(
                noDuplicates,
                CompetitionSetupMatchDto::participants
            ),
        ),
        this::groups validate allOf(
            collection,
            noDuplicates(
                CompetitionSetupGroupDto::weighting,
            )
        ),
        this::statisticEvaluations validate allOf(
            collection,
            noDuplicates(
                CompetitionSetupGroupStatisticEvaluationDto::name,
            ),
            noDuplicates(
                CompetitionSetupGroupStatisticEvaluationDto::priority,
            )
        ),
        ValidationResult.oneOf(
            ValidationResult.allOf(
                this::matches validate notNull,
            ),
            ValidationResult.allOf(
                this::groups validate notNull,
                this::statisticEvaluations validate notNull,
            )
        ),
        this::places validate collection,
        ValidationResult.oneOf(
            this::placesOption validate isValue(CompetitionSetupPlacesOption.CUSTOM),
            this::places validate isNull
        ),
        ValidationResult.oneOf(
            ValidationResult.allOf(
                this::placesOption validate isValue(CompetitionSetupPlacesOption.CUSTOM),
                this::matches validate collection(select(notNull, CompetitionSetupMatchDto::teams))
            ),
            ValidationResult.allOf(
                this::placesOption validate isNotValue(CompetitionSetupPlacesOption.CUSTOM),
            ),
        )
    )


    companion object {
        val example
            get() = CompetitionSetupRoundDto(
                name = "Round name",
                required = false,
                matches = listOf(CompetitionSetupMatchDto.example), // todo: should provide 2 examples (one with matches, one with groups) or extra details/description
                groups = listOf(CompetitionSetupGroupDto.example),
                statisticEvaluations = listOf(CompetitionSetupGroupStatisticEvaluationDto.example),
                useDefaultSeeding = true,
                placesOption = CompetitionSetupPlacesOption.CUSTOM,
                places = listOf(CompetitionSetupPlaceDto.example),
            )
    }
}