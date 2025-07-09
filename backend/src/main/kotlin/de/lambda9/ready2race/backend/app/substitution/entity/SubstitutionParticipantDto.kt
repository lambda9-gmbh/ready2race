package de.lambda9.ready2race.backend.app.substitution.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.util.*

data class SubstitutionParticipantDto(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val year: Int,
    val gender: Gender,
    val external: Boolean?,
    val externalClubName: String?,
)