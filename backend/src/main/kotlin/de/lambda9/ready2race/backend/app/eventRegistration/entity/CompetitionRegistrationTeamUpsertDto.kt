package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import java.util.*

data class CompetitionRegistrationTeamUpsertDto(
    val id: UUID?,
    val clubId: UUID?,
    val optionalFees: List<UUID>?,
    val namedParticipants: List<CompetitionRegistrationNamedParticipantUpsertDto>,
    val ratingCategory: UUID?,
    val callbackUrl: String? = null,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::callbackUrl validate notNull
    )

    companion object {
        val example
            get() = CompetitionRegistrationTeamUpsertDto(
                id = UUID.randomUUID(),
                clubId = UUID.randomUUID(),
                optionalFees = emptyList(),
                namedParticipants = emptyList(),
                ratingCategory = UUID.randomUUID(),
                callbackUrl = "ready2race.info/event/${UUID.randomUUID()}/challenge/",
            )
    }
}