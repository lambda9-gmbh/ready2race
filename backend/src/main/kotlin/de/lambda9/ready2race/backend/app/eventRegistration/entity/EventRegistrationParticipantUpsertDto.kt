package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.*

data class EventRegistrationParticipantUpsertDto (
    val id: UUID,
    val isNew: Boolean?,
    val hasChanged: Boolean?,
    val firstname: String,
    val lastname: String,
    val year: Int?,
    val gender: Gender,
    val external: Boolean?,
    val externalClubName: String?,
    val competitionsSingle: List<CompetitionRegistrationSingleUpsertDto>?,
): Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.Valid
}