package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class ParticipantImportRequest(
    val separator: Char,
    val colFirstname: String,
    val colLastname: String,
    val colYear: String,
    val colGender: String,
    val colExternalClubname: String?,
) : Validatable {

    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {

        val example get() = ParticipantImportRequest(
            separator = ',',
            colFirstname = "First name",
            colLastname = "Last name",
            colYear = "Year",
            colGender = "Gender",
            colExternalClubname = "External Club",
        )

    }

}
