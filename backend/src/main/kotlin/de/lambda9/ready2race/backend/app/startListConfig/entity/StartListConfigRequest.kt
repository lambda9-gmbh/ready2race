package de.lambda9.ready2race.backend.app.startListConfig.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import kotlin.String

data class StartListConfigRequest(
    val name: String,
    val colParticipantFirstname: String?,
    val colParticipantLastname: String?,
    val colParticipantGender: String?,
    val colParticipantRole: String?,
    val colParticipantYear: String?,
    val colParticipantClub: String?,
    val colClubName: String?,
    val colTeamName: String?,
    val colTeamStartNumber: String?,
    val colMatchName: String?,
    val colMatchStartTime: String?,
    val colRoundName: String?,
    val colCompetitionIdentifier: String?,
    val colCompetitionName: String?,
    val colCompetitionShortName: String?,
    val colCompetitionCategory: String?,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::name validate notBlank,
            ValidationResult.anyOf(
                this::colParticipantFirstname validate allOf(notNull, notBlank),
                this::colParticipantLastname validate allOf(notNull, notBlank),
                this::colParticipantGender validate allOf(notNull, notBlank),
                this::colParticipantRole validate allOf(notNull, notBlank),
                this::colParticipantYear validate allOf(notNull, notBlank),
                this::colParticipantClub validate allOf(notNull, notBlank),
                this::colClubName validate allOf(notNull, notBlank),
                this::colTeamName validate allOf(notNull, notBlank),
                this::colTeamStartNumber validate allOf(notNull, notBlank),
                this::colMatchName validate allOf(notNull, notBlank),
                this::colMatchStartTime validate allOf(notNull, notBlank),
                this::colRoundName validate allOf(notNull, notBlank),
                this::colCompetitionIdentifier validate allOf(notNull, notBlank),
                this::colCompetitionName validate allOf(notNull, notBlank),
                this::colCompetitionShortName validate allOf(notNull, notBlank),
                this::colCompetitionCategory validate allOf(notNull, notBlank),
            )
        )

    companion object {

        val example get() = StartListConfigRequest(
            name = "Einzelrennen",
            colTeamStartNumber = "Start Number",
            colParticipantFirstname = null,
            colParticipantLastname = null,
            colParticipantGender = null,
            colParticipantRole = null,
            colParticipantYear = null,
            colParticipantClub = null,
            colClubName = null,
            colTeamName = null,
            colMatchName = null,
            colMatchStartTime = null,
            colRoundName = null,
            colCompetitionIdentifier = null,
            colCompetitionName = null,
            colCompetitionShortName = null,
            colCompetitionCategory = null,
        )
    }
}
