package de.lambda9.ready2race.backend.app.participantRequirement.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.*

data class ParticipantRequirementCheckForEventConfigDto(
    val requirementId: UUID,
    val separator: Char?,
    val charset: String?,
    val firstnameColName: String,
    val lastnameColName: String,
    val yearsColName: String?,
    val clubColName: String?,
    val requirementColName: String?,
    val requirementIsValidValue: String?
) : Validatable {

    fun getColNames() = listOfNotNull(
        this.firstnameColName,
        this.lastnameColName,
        this.yearsColName,
        this.clubColName,
        this.requirementColName,
    )

    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = ParticipantRequirementCheckForEventConfigDto(
                requirementId = UUID.randomUUID(),
                separator = ';',
                charset = Charsets.UTF_8.toString(),
                firstnameColName = "firstname",
                lastnameColName = "lastname",
                yearsColName = "year",
                clubColName = "club",
                requirementColName = "active",
                requirementIsValidValue = "true"
            )
    }
}