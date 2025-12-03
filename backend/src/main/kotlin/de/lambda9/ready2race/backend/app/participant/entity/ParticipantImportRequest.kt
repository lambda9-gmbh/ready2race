package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

data class ParticipantImportRequest(
    val separator: Char,
    val charset: String,
    val colFirstname: String,
    val colLastname: String,
    val colYear: String,
    val colGender: String,
    val colExternalClubname: String?,
    val colEmail: String?,
    val noHeader: Boolean,
    val valueGenderMale: String,
    val valueGenderFemale: String,
    val valueGenderDiverse: String,
) : Validatable {

    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {

        val example get() = ParticipantImportRequest(
            separator = ',',
            charset = "UTF-8",
            colFirstname = "First name",
            colLastname = "Last name",
            colYear = "Year",
            colGender = "Gender",
            colExternalClubname = "External Club",
            colEmail = "Email",
            noHeader = false,
            valueGenderMale = "M",
            valueGenderFemale = "F",
            valueGenderDiverse = "D",
        )

    }

}
