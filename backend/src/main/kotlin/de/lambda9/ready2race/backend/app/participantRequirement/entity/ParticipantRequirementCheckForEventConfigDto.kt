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
    val noHeader: Boolean,
    val requirementColName: String?,
    /**
     * Die Werte der Bedingungsspalte, die als erfüllt gelten. Mehrere sind erlaubt - die
     * DRV-Aktivenpassliste etwa führt "ja" und "erweitert" nebeneinander, beides bedeutet
     * startberechtigt. Leer oder nicht gesetzt heißt: jede Zeile zählt.
     */
    val requirementIsValidValues: List<String>?
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
                noHeader = false,
                requirementColName = "active",
                requirementIsValidValues = listOf("true", "yes")
            )
    }
}